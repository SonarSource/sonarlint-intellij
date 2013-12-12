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
package org.sonar.ide.intellij.inspection;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.config.SonarQubeSettings;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.model.ISonarIssue;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.wsclient.ISonarWSClientFacade;
import org.sonar.ide.intellij.wsclient.SonarWSClientException;
import org.sonar.ide.intellij.wsclient.WSClientFactory;
import org.sonar.wsclient.jsonsimple.JSONArray;
import org.sonar.wsclient.jsonsimple.JSONObject;
import org.sonar.wsclient.jsonsimple.JSONValue;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SonarQubeInspectionContext implements GlobalInspectionContextExtension<SonarQubeInspectionContext> {

  private static final Logger LOG = Logger.getInstance(SonarQubeInspectionContext.class);

  private Map<PsiFile, List<ISonarIssue>> remoteIssuesByFile = new HashMap<PsiFile, List<ISonarIssue>>();
  private SonarQubeServer server;
  private boolean debugEnabled;
  private Map<String, PsiFile> resourceCache;
  private SonarQubeConsole console;
  private SonarQubeIssueCache issueCache;

  public static final Key<SonarQubeInspectionContext> KEY = Key.create("SonarQubeInspectionContext");

  @NotNull
  @Override
  public Key<SonarQubeInspectionContext> getID() {
    return KEY;
  }

  public Map<PsiFile, List<ISonarIssue>> getRemoteIssuesByFile() {
    return remoteIssuesByFile;
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public SonarQubeServer getServer() {
    return server;
  }

  public SonarQubeIssueCache getIssueCache() {
    return issueCache;
  }

  @Override
  public void performPreRunActivities(@NotNull List<Tools> globalTools, @NotNull List<Tools> localTools, @NotNull GlobalInspectionContext context) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    Project p = context.getProject();
    issueCache = p.getComponent(SonarQubeIssueCache.class);
    console = SonarQubeConsole.getSonarQubeConsole(p);
    console.clear();
    ProjectSettings projectSettings = p.getComponent(ProjectSettings.class);
    String serverId = projectSettings.getServerId();
    String projectKey = projectSettings.getProjectKey();
    if (serverId == null || projectKey == null) {
      console.error("Project is not associated to SonarQube");
      return;
    }
    SonarQubeSettings settings = SonarQubeSettings.getInstance();
    server = settings.getServer(serverId);
    if (server == null) {
      console.error("Project was associated to a server that is not configured: " + serverId);
      return;
    }
    populateResourceCache(context, p, projectSettings);

    fetchRemoteIssues(projectKey);

    debugEnabled = projectSettings.isVerboseEnabled();
    String jvmArgs = "";

    try {
      File jsonReport = new SonarRunnerAnalysis().analyzeProject(indicator, p, projectSettings, server, debugEnabled, jvmArgs);
      if (jsonReport != null && !indicator.isCanceled()) {
        createIssuesFromReportOutput(jsonReport);
      }
    } catch (Exception e) {
      console.error(e.getMessage());
      LOG.warn("Error during execution of SonarQube analysis", e);
    }
  }

  private void fetchRemoteIssues(String projectKey) {
    ISonarWSClientFacade sonarClient = WSClientFactory.getInstance().getSonarClient(server);
    List<ISonarIssue> remoteIssues;
    try {
      remoteIssues = sonarClient.getUnresolvedRemoteIssuesRecursively(projectKey);
    } catch (SonarWSClientException e) {
      LOG.warn("Unable to retrieve remote issues", e);
      console.error("Unable to retrieve remote issues: " + e.getMessage());
      return;
    }
    for (ISonarIssue remoteIssue : remoteIssues) {
      PsiFile resource = resourceCache.get(remoteIssue.resourceKey());
      if (resource == null) {
        continue;
      }
      if (!remoteIssuesByFile.containsKey(resource)) {
        remoteIssuesByFile.put(resource, new ArrayList<ISonarIssue>());
      }
      remoteIssuesByFile.get(resource).add(remoteIssue);
    }
  }

  private void populateResourceCache(final GlobalInspectionContext globalContext, final Project p, final ProjectSettings projectSettings) {
    resourceCache = new HashMap<String, PsiFile>();
    final SearchScope searchScope = globalContext.getRefManager().getScope().toSearchScope();
    for (final VirtualFile virtualFile : FileTypeIndex.getFiles(JavaFileType.INSTANCE, (GlobalSearchScope) searchScope)) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          PsiFile psiFile = PsiManager.getInstance(globalContext.getProject()).findFile(virtualFile);
          PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
          Module module = ProjectRootManager.getInstance(p).getFileIndex().getModuleForFile(virtualFile);
          if (module == null) {
            console.error("Unable to find module for file " + virtualFile.getName());
            return;
          }
          String sonarKeyOfModule = projectSettings.getModuleKeys().get(module.getName());
          if (sonarKeyOfModule == null) {
            console.info("Module " + module.getName() + " is not associated to SonarQube");
            sonarKeyOfModule = module.getName();
          }
          resourceCache.put(InspectionUtils.getComponentKey(sonarKeyOfModule, psiJavaFile), psiJavaFile);
        }
      });
    }
  }


  private void createIssuesFromReportOutput(File outputFile) {
    issueCache.getLocalIssuesByElement().clear();
    issueCache.getModifiedFile().clear();
    FileReader fileReader = null;
    try {
      fileReader = new FileReader(outputFile);
      Object obj = JSONValue.parse(fileReader);
      JSONObject sonarResult = (JSONObject) obj;
      // Start by registering all modified components
      final JSONArray components = (JSONArray) sonarResult.get("components");
      for (Object component : components) {
        String key = ObjectUtils.toString(((JSONObject) component).get("key"));
        PsiFile file = resourceCache.get(key);
        if (file != null) {
          issueCache.getModifiedFile().add(file);
        }
      }
      // Now read all rules name in a cache
      final Map<String, String> ruleByKey = new HashMap<String, String>();
      final JSONArray rules = (JSONArray) sonarResult.get("rules");
      for (Object rule : rules) {
        String key = ObjectUtils.toString(((JSONObject) rule).get("key"));
        String name = ObjectUtils.toString(((JSONObject) rule).get("name"));
        ruleByKey.put(key, name);
      }
      // Now iterate over all issues and store them
      for (Object issueObj : (JSONArray) sonarResult.get("issues")) {
        final JSONObject jsonIssue = (JSONObject) issueObj;
        ISonarIssue issue = new SonarIssueFromJsonReport(jsonIssue, ruleByKey);
        PsiFile file = resourceCache.get(issue.resourceKey());
        if (file == null) {
          continue;
        }
        if (!issueCache.getLocalIssuesByElement().containsKey(file)) {
          issueCache.getLocalIssuesByElement().put(file, new ArrayList<IssueOnPsiElement>());
        }
        issueCache.getLocalIssuesByElement().get(file).add(new IssueOnPsiElement(file, issue));
      }
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    } finally {
      IOUtils.closeQuietly(fileReader);
    }
  }

  @Override
  public void performPostRunActivities(@NotNull java.util.List<InspectionToolWrapper> inspections, @NotNull GlobalInspectionContext context) {
    Project p = context.getProject();
    DaemonCodeAnalyzer.getInstance(p).restart();
  }
  @Override
  public void cleanup() {
    // Nothing to do
  }

}
