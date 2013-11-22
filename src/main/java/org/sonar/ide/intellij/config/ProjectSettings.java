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
package org.sonar.ide.intellij.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.wsclient.ISonarWSClientFacade;
import org.sonar.ide.intellij.wsclient.WSClientFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@State(
    name = "SonarQubeConfiguration",
    storages = {
        @Storage(id = "default", file = StoragePathMacros.PROJECT_FILE),
        @Storage(id = "dir", file = StoragePathMacros.PROJECT_CONFIG_DIR + "/sonarqube.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public final class ProjectSettings implements PersistentStateComponent<ProjectSettings>, ProjectComponent {

  private static final Logger LOG = Logger.getInstance(ProjectSettings.class);

  private String serverId = null;
  private String projectKey = null;
  private Map<String, String> moduleKeys = new HashMap<String, String>();

  private ISonarWSClientFacade sonarClient;

  public
  @CheckForNull
  String getServerId() {
    return serverId;
  }

  public void setServerId(@Nullable String serverId) {
    this.serverId = serverId;
  }

  @CheckForNull
  public String getProjectKey() {
    return projectKey;
  }

  public void setProjectKey(@Nullable String projectKey) {
    this.projectKey = projectKey;
  }

  public Map<String, String> getModuleKeys() {
    return moduleKeys != null ? moduleKeys : new HashMap<String, String>();
  }

  public void setModuleKeys(Map<String, String> moduleKeys) {
    this.moduleKeys = moduleKeys;
  }

  public ProjectSettings getState() {
    return this;
  }

  public void loadState(ProjectSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public void projectOpened() {
    // Nothing to do
  }

  @Override
  public void projectClosed() {
    // Nothing to do
  }

  @Override
  public void initComponent() {
    // Nothing to do
  }

  @Override
  public void disposeComponent() {
    // Nothing to do
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "ProjectSettings";
  }
}
