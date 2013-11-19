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

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
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
import org.apache.commons.lang.StringUtils;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.config.SonarQubeSettings;
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
import java.util.*;

public class SonarQubeInspectionContext implements GlobalInspectionContextExtension<SonarQubeInspectionContext> {

  private static final Logger LOG = Logger.getInstance(SonarQubeInspectionContext.class);
  private static final char DELIMITER = ':';
  private static final char PACKAGE_DELIMITER = '.';
  public static final String DEFAULT_PACKAGE_NAME = "[default]";

  private List<ISonarIssue> remoteIssues = new ArrayList<ISonarIssue>();
  private List<ISonarIssue> localIssues = new ArrayList<ISonarIssue>();
  private List<ISonarIssue> localNewIssues = new ArrayList<ISonarIssue>();
  private List<String> modifiedFiles;
  private SonarQubeServer server;
  private boolean debugEnabled;
  private Map<String, PsiFile> resourceCache;

  public static final Key<SonarQubeInspectionContext> KEY = Key.create("SonarQubeInspectionContext");

  @Override
  public Key<SonarQubeInspectionContext> getID() {
    return KEY;
  }

  public List<ISonarIssue> getRemoteIssues() {
    return remoteIssues;
  }

  public List<ISonarIssue> getLocalIssues() {
    return localIssues;
  }

  public List<ISonarIssue> getLocalNewIssues() {
    return localNewIssues;
  }

  public List<String> getModifiedFileKeys() {
    return modifiedFiles;
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public SonarQubeServer getServer() {
    return server;
  }

  @Override
  public void performPreRunActivities(List<Tools> globalTools, List<Tools> localTools, GlobalInspectionContext context) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    Project p = context.getProject();
    if (p != null) {
      ProjectSettings projectSettings = p.getComponent(ProjectSettings.class);
      String serverId = projectSettings.getServerId();
      if (serverId == null) {
        System.out.println("Project is not associated to SonarQube");
        return;
      }
      SonarQubeSettings settings = SonarQubeSettings.getInstance();
      server = settings.getServer(serverId);
      if (server == null) {
        System.out.println("Project was associated to a server that is not configured: " + serverId);
        return;
      }
      ISonarWSClientFacade sonarClient = WSClientFactory.getInstance().getSonarClient(server);
      try {
        remoteIssues = sonarClient.getRemoteIssuesRecursively(projectSettings.getProjectKey());
      } catch (SonarWSClientException e) {
        LOG.warn("Unable to retrieve remote issues", e);
        return;
      }

      debugEnabled = LOG.isDebugEnabled();
      String jvmArgs = "";

      MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(p);
      ModuleManager moduleManager = ModuleManager.getInstance(p);
      Module[] ijModules = moduleManager.getModules();
      File jsonReport = null;
      if (mavenProjectsManager.isMavenizedProject()) {
         // Use Maven
         jsonReport = new MavenAnalysis().runMavenAnalysis(p, projectSettings, server, debugEnabled);
      } else if (ijModules.length == 1) {
        // Use SQ Runner
        jsonReport = new SonarRunnerAnalysis().analyzeSingleModuleProject(indicator, p, projectSettings, server, debugEnabled, jvmArgs);
      } else {
        LOG.warn("Local analysis is not supported for your project");
      }
      if (jsonReport != null) {
        createIssuesFromReportOutput(jsonReport);
      }
    }
  }

  public Map<String, PsiFile> getResourceCache(final GlobalInspectionContext globalContext, final Project p, final ProjectSettings projectSettings) {
    if (resourceCache == null) {
      resourceCache = new HashMap<String, PsiFile>();
      final SearchScope searchScope = globalContext.getRefManager().getScope().toSearchScope();
      for (final VirtualFile virtualFile : FileTypeIndex.getFiles(JavaFileType.INSTANCE, (GlobalSearchScope) searchScope)) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            PsiFile psiFile = PsiManager.getInstance(globalContext.getProject()).findFile(virtualFile);
            PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
            Module module = ProjectRootManager.getInstance(p).getFileIndex().getModuleForFile(virtualFile);
            String sonarKeyOfModule = projectSettings.getModuleKeys().get(module.getName());
            if (sonarKeyOfModule == null) {
              LOG.warn("Module " + module.getName() + " is not associated to SonarQube");
            } else {
              resourceCache.put(getComponentKey(sonarKeyOfModule, psiJavaFile), psiJavaFile);
            }
          }
        });
      }
    }
    return resourceCache;
  }

  private String getComponentKey(String moduleKey, PsiFile file) {
    if (file instanceof PsiJavaFile) {
      return getJavaComponentKey(moduleKey, (PsiJavaFile) file);
    }
    final StringBuilder result = new StringBuilder();
    result.append(moduleKey).append(":");
    final VirtualFile virtualFile = file.getVirtualFile();
    if (null != virtualFile) {
      final String filePath = virtualFile.getPath();

      VirtualFile sourceRootForFile = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getSourceRootForFile(virtualFile);
      // getSourceRootForFile doesn't work in phpstorm for some reasons
      if (null == sourceRootForFile) {
        sourceRootForFile = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getContentRootForFile(virtualFile);
      }

      if (sourceRootForFile != null) {
        final String sourceRootForFilePath = sourceRootForFile.getPath() + "/";

        String baseFileName = filePath.replace(sourceRootForFilePath, "");

        if (baseFileName.equals(file.getName())) {
          result.append("[root]/");
        }

        result.append(baseFileName);
      }
    }
    return result.toString();
  }

  private String getJavaComponentKey(String moduleKey, PsiJavaFile file) {
    String result = null;
    String packageName = file.getPackageName();
    if (StringUtils.isWhitespace(packageName)) {
      packageName = DEFAULT_PACKAGE_NAME;
    }
    String fileName = StringUtils.substringBeforeLast(file.getName(), ".");
    if (moduleKey != null && packageName != null) {
      result = new StringBuilder()
          .append(moduleKey).append(DELIMITER).append(packageName)
          .append(PACKAGE_DELIMITER).append(fileName)
          .toString();
    }
    return result;
  }

  private void createIssuesFromReportOutput(File outputFile) {
    this.modifiedFiles = new ArrayList<String>();
    this.localIssues = new ArrayList<ISonarIssue>();
    this.localNewIssues = new ArrayList<ISonarIssue>();
    FileReader fileReader = null;
    try {
      fileReader = new FileReader(outputFile);
      Object obj = JSONValue.parse(fileReader);
      JSONObject sonarResult = (JSONObject) obj;
      // Start by registering all modified components
      final JSONArray components = (JSONArray) sonarResult.get("components");
      for (Object component : components) {
        String key = ObjectUtils.toString(((JSONObject) component).get("key"));
        modifiedFiles.add(key);
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
        boolean isNew = Boolean.TRUE.equals(jsonIssue.get("isNew"));
        if (isNew) {
          localNewIssues.add(new SonarIssueFromJsonReport(jsonIssue, ruleByKey));
        } else {
          localIssues.add(new SonarIssueFromJsonReport(jsonIssue, ruleByKey));
        }
      }
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    } finally {
      IOUtils.closeQuietly(fileReader);
    }
  }

  @Override
  public void performPostRunActivities(List<InspectionProfileEntry> inspections, GlobalInspectionContext context) {
  }

  @Override
  public void cleanup() {
  }

}
