/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.intellij.wsclient;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.sonar.ide.intellij.model.ISonarIssue;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.wsclient.Sonar;
import org.sonar.wsclient.SonarClient;
import org.sonar.wsclient.connectors.ConnectionException;
import org.sonar.wsclient.issue.Issue;
import org.sonar.wsclient.issue.IssueQuery;
import org.sonar.wsclient.issue.Issues;
import org.sonar.wsclient.rule.Rule;
import org.sonar.wsclient.services.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class SonarWSClientFacade implements ISonarWSClientFacade {

  private final Sonar sonar;
  private final SonarClient sonarClient;
  private final SonarQubeServer server;

  public SonarWSClientFacade(final Sonar sonar, final SonarClient sonarClient, final SonarQubeServer server) {
    this.sonar = sonar;
    this.sonarClient = sonarClient;
    this.server = server;
  }

  @Override
  public ConnectionTestResult testConnection() {
    try {
      Authentication auth = sonar.find(new AuthenticationQuery());
      if (auth.isValid()) {
        return ConnectionTestResult.OK;
      } else {
        return ConnectionTestResult.AUTHENTICATION_ERROR;
      }
    } catch (ConnectionException e) {
      Logger.getInstance(getClass()).error("Unable to connect", e);
      return ConnectionTestResult.CONNECT_ERROR;
    }
  }

  @Override
  public String getServerVersion() {
    return find(new ServerQuery()).getVersion();
  }

  @Override
  public List<ISonarRemoteProject> listAllRemoteProjects() {
    ResourceQuery query = new ResourceQuery().setScopes(Resource.SCOPE_SET).setQualifiers(Resource.QUALIFIER_PROJECT);
    List<Resource> resources = findAll(query);
    List<ISonarRemoteProject> result = new ArrayList<ISonarRemoteProject>(resources.size());
    for (Resource resource : resources) {
      result.add(new SonarRemoteModule(resource, server));
    }
    return result;
  }

  private <M extends Model> M find(Query<M> query) {
    try {
      return sonar.find(query);
    } catch (ConnectionException e) {
      throw new org.sonar.ide.intellij.wsclient.ConnectionException(e);
    }
  }

  private List<Resource> findAll(ResourceQuery query) {
    try {
      return sonar.findAll(query);
    } catch (ConnectionException e) {
      throw new org.sonar.ide.intellij.wsclient.ConnectionException(e);
    }
  }

  @Override
  public List<ISonarRemoteProject> searchRemoteProjects(String text) {
    List<ISonarRemoteProject> result;
    if (text.length() < 3) {
      ResourceQuery query = new ResourceQuery()
          .setScopes(Resource.SCOPE_SET)
          .setQualifiers(Resource.QUALIFIER_PROJECT)
          .setResourceKeyOrId(text);
      List<Resource> resources = findAll(query);

      result = new ArrayList<ISonarRemoteProject>(resources.size());
      for (Resource resource : resources) {
        result.add(new SonarRemoteModule(resource, server));
      }
    } else {
      ResourceSearchQuery query = ResourceSearchQuery.create(text).setQualifiers(Resource.QUALIFIER_PROJECT);
      ResourceSearchResult searchResult = find(query);

      result = new ArrayList<ISonarRemoteProject>(searchResult.getResources().size());
      for (ResourceSearchResult.Resource resource : searchResult.getResources()) {
        result.add(new SonarRemoteModule(resource, server));
      }
    }

    return result;
  }

  @Override
  public List<ISonarRemoteModule> getRemoteModules(ISonarRemoteProject project) {
    ResourceQuery query = new ResourceQuery()
        .setAllDepths()
        .setScopes(Resource.SCOPE_SET)
        .setQualifiers(Resource.QUALIFIER_MODULE)
        .setResourceKeyOrId(project.getKey());
    List<Resource> resources = findAll(query);
    List<ISonarRemoteModule> result = new ArrayList<ISonarRemoteModule>(resources.size());
    for (Resource resource : resources) {
      result.add(new SonarRemoteModule(resource, server));
    }
    return result;
  }

  @Override
  public boolean exists(String resourceKey) {
    return find(new ResourceQuery().setResourceKeyOrId(resourceKey)) != null;
  }

  @Override
  public Date getLastAnalysisDate(String resourceKey) {
    Resource remoteResource = find(ResourceQuery.createForMetrics(resourceKey));
    if (remoteResource != null) {
      return remoteResource.getDate();
    }
    return null;
  }

  @Override
  public String[] getRemoteCode(String resourceKey) {
    Source source = find(SourceQuery.create(resourceKey));
    String[] remote = new String[source.getLinesById().lastKey()];
    for (int i = 0; i < remote.length; i++) {
      remote[i] = source.getLine(i + 1);
      if (remote[i] == null) {
        remote[i] = "";
      }
    }
    return remote;
  }

  @Override
  public List<ISonarIssue> getUnresolvedRemoteIssuesRecursively(String resourceKey) {
    int maxPageSize = -1;
    List<ISonarIssue> result = new ArrayList<ISonarIssue>();
    int pageIndex = 1;
    Issues issues;
    do {
      issues = findIssues(IssueQuery.create().componentRoots(resourceKey).resolved(false).pageSize(maxPageSize).pageIndex(pageIndex));
      for (Issue issue : issues.list()) {
        result.add(new SonarRemoteIssue(issue, issues.rule(issue)));
      }
    } while (pageIndex++ < issues.paging().pages());
    return result;
  }

  @Override
  public List<ISonarIssue> getUnresolvedRemoteIssues(String resourceKey) {
    Issues issues = findIssues(IssueQuery.create().components(resourceKey).resolved(false));
    List<ISonarIssue> result = new ArrayList<ISonarIssue>(issues.list().size());
    for (Issue issue : issues.list()) {
      result.add(new SonarRemoteIssue(issue, issues.rule(issue)));
    }
    return result;
  }

  private Issues findIssues(IssueQuery query) {
    try {
      return sonarClient.issueClient().find(query);
    } catch (ConnectionException e) {
      throw new org.sonar.ide.intellij.wsclient.ConnectionException(e);
    } catch (Exception e) {
      throw new org.sonar.ide.intellij.wsclient.SonarWSClientException("Error during issue query " + query.toString(), e);
    }
  }

  @Override
  public String[] getChildrenKeys(String resourceKey) {
    ResourceQuery query = new ResourceQuery().setDepth(1).setResourceKeyOrId(resourceKey);
    Collection<Resource> resources = findAll(query);
    List<String> result = new ArrayList<String>();
    for (Resource resource : resources) {
      result.add(resource.getKey());
    }
    return result.toArray(new String[result.size()]);
  }

  private static class SonarRemoteIssue implements ISonarIssue {

    private final Issue remoteIssue;
    private Rule rule;

    public SonarRemoteIssue(final Issue remoteIssue, final Rule rule) {
      this.remoteIssue = remoteIssue;
      this.rule = rule;
    }

    @Override
    public String key() {
      return remoteIssue.key();
    }

    @Override
    public String resourceKey() {
      return remoteIssue.componentKey();
    }

    @Override
    public boolean resolved() {
      return StringUtils.isNotBlank(remoteIssue.resolution());
    }

    @Override
    public Integer line() {
      return remoteIssue.line();
    }

    @Override
    public String severity() {
      return remoteIssue.severity();
    }

    @Override
    public String message() {
      return remoteIssue.message();
    }

    @Override
    public String ruleKey() {
      return rule.key();
    }

    @Override
    public String ruleName() {
      return rule.name();
    }

    @Override
    public String assignee() {
      return remoteIssue.assignee();
    }

    @Override
    public boolean isNew() {
      return false;
    }
  }

  private static class SonarRemoteModule implements ISonarRemoteProject, ISonarRemoteModule {

    private String key;
    private String name;
    private SonarQubeServer server;

    public SonarRemoteModule(final Resource resource, final SonarQubeServer server) {
      this.key = resource.getKey();
      this.name = resource.getName();
      this.server = server;
    }

    public SonarRemoteModule(final ResourceSearchResult.Resource resource, final SonarQubeServer server) {
      this.key = resource.key();
      this.name = resource.name();
      this.server = server;
    }

    @Override
    public String getKey() {
      return this.key;
    }

    @Override
    public String getName() {
      return this.name;
    }

    @Override
    public SonarQubeServer getServer() {
      return server;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SonarRemoteModule)) {
        return false;
      }
      SonarRemoteModule other = (SonarRemoteModule) obj;
      return server.getId().equals(other.getServer().getId()) && getKey().equals(other.getKey());
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(getKey()).append(getServer().getId()).toHashCode();
    }
  }

}
