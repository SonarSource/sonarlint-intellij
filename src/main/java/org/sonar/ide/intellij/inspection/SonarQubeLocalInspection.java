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

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
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

public class SonarQubeLocalInspection extends LocalInspectionTool {

  private static final Logger LOG = Logger.getInstance(SonarQubeLocalInspection.class);
  private static final char DELIMITER = ':';
  private static final char PACKAGE_DELIMITER = '.';
  public static final String DEFAULT_PACKAGE_NAME = "[default]";

  private static Map<String, List<ISonarIssue>> localIssuesByFileKey = new HashMap<String, List<ISonarIssue>>();
  private static List<String> modifiedFiles = new ArrayList<String>();


  private SonarQubeServer server;
  private ProjectSettings projectSettings;
  private SonarQubeConsole console;
  private ISonarWSClientFacade sonarClient;
  private boolean localAnalysisDone = false;

  @Override
  public void inspectionStarted(LocalInspectionToolSession session, boolean isOnTheFly) {
    final Project p = session.getFile().getProject();
    console = SonarQubeConsole.getSonarQubeConsole(p);
    projectSettings = p.getComponent(ProjectSettings.class);
    String serverId = projectSettings.getServerId();
    if (serverId == null) {
      console.error("Project is not associated to SonarQube");
      return;
    }
    SonarQubeSettings settings = SonarQubeSettings.getInstance();
    server = settings.getServer(serverId);
    if (server == null) {
      console.error("Project was associated to a server that is not configured: " + serverId);
      return;
    }
    sonarClient = WSClientFactory.getInstance().getSonarClient(server);
    if (!isOnTheFly && !localAnalysisDone) {
      localAnalysisDone = true;
      System.out.println("FOOOOOOOOO BAAAAAAAAAAAAR");
      runLocalAnalysis(p, projectSettings, server);
    }
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof PsiJavaFile)) {
      return null;
    }
    ProgressManager.checkCanceled();
    PsiJavaFile psiJavaFile = (PsiJavaFile) file;
    Module module = ProjectRootManager.getInstance(file.getProject()).getFileIndex().getModuleForFile(file.getVirtualFile());
    String sonarKeyOfModule = projectSettings.getModuleKeys().get(module.getName());
    if (sonarKeyOfModule == null) {
      console.error("Module " + module.getName() + " is not associated to SonarQube");
      return null;
    }
    String componentKey = getComponentKey(sonarKeyOfModule, psiJavaFile);
    List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();

    if (!modifiedFiles.contains(componentKey)) {
      populateRemoteIssues(file, manager, console, server, componentKey, problems, isOnTheFly);
    } else {
      if (localIssuesByFileKey.containsKey(componentKey)) {
        for (final ISonarIssue issue : localIssuesByFileKey.get(componentKey)) {
          problems.add(computeIssueProblemDescriptor(file, issue, manager, isOnTheFly));
        }
      }
    }
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private void populateRemoteIssues(PsiFile file, InspectionManager manager, SonarQubeConsole console, SonarQubeServer server, String componentKey, List<ProblemDescriptor> problems, boolean isOnTheFly) {
    try {
      List<ISonarIssue> remoteIssues = sonarClient.getUnresolvedRemoteIssues(componentKey);
      for (final ISonarIssue issue : remoteIssues) {
        problems.add(computeIssueProblemDescriptor(file, issue, manager, isOnTheFly));
      }
    } catch (SonarWSClientException e) {
      LOG.warn("Unable to retrieve remote issues", e);
      console.error("Unable to retrieve remote issues: " + e.getMessage());
    }
  }

  private void runLocalAnalysis(Project p, ProjectSettings projectSettings, SonarQubeServer server) {
    modifiedFiles.clear();
    localIssuesByFileKey.clear();
    boolean debugEnabled = LOG.isDebugEnabled();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    SonarQubeConsole console = SonarQubeConsole.getSonarQubeConsole(p);
    String jvmArgs = "";

    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(p);
    ModuleManager moduleManager = ModuleManager.getInstance(p);
    Module[] ijModules = moduleManager.getModules();
    File jsonReport = null;
    if (mavenProjectsManager.isMavenizedProject()) {
      // Use Maven
      jsonReport = new MavenAnalysis().runMavenAnalysis(indicator, p, projectSettings, server, debugEnabled);
    } else if (ijModules.length == 1) {
      // Use SQ Runner
      jsonReport = new SonarRunnerAnalysis().analyzeSingleModuleProject(indicator, p, projectSettings, server, debugEnabled, jvmArgs);
    } else {
      console.error("Local analysis is not supported for your project");
    }
    if (jsonReport != null) {
      createIssuesFromReportOutput(jsonReport);
    }
  }

  private void createIssuesFromReportOutput(File outputFile) {
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
        ISonarIssue issue = new SonarIssueFromJsonReport(jsonIssue, ruleByKey);
        if (!localIssuesByFileKey.containsKey(issue.resourceKey())) {
          localIssuesByFileKey.put(issue.resourceKey(), new ArrayList<ISonarIssue>());
        }
        localIssuesByFileKey.get(issue.resourceKey()).add(issue);
      }
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    } finally {
      IOUtils.closeQuietly(fileReader);
    }
  }


  public static String getComponentKey(String moduleKey, PsiFile file) {
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

  private static String getJavaComponentKey(String moduleKey, PsiJavaFile file) {
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

  @Nullable
  public static ProblemDescriptor computeIssueProblemDescriptor(PsiFile psiFile, ISonarIssue issue, InspectionManager manager, boolean isOnTheFly) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
    Document document = documentManager.getDocument(psiFile.getContainingFile());
    if (document == null) {
      return null;
    }
    String message = issue.message();
    if (issue.isNew()) {
      message = "NEW: " + message;
    }
    Integer line = issue.line();
    TextRange range = getTextRange(document, line != null ? line : 1);
    return manager.createProblemDescriptor(psiFile, range,
        message,
        issue.isNew() ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.WEAK_WARNING,
        isOnTheFly
    );
  }

  @NotNull
  private static TextRange getTextRange(@NotNull Document document, int line) {
    int lineStartOffset = document.getLineStartOffset(line - 1);
    int lineEndOffset = document.getLineEndOffset(line - 1);
    return new TextRange(lineStartOffset, lineEndOffset);
  }

  private static ProblemHighlightType issueToProblemHighlightType(ISonarIssue issue) {
    if (StringUtils.isBlank(issue.severity())) {
      return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    } else {
      String sonarSeverity = issue.severity();
      if (ISonarIssue.BLOCKER.equals(sonarSeverity)) {
        return ProblemHighlightType.ERROR;
      } else if (ISonarIssue.CRITICAL.equals(sonarSeverity) || ISonarIssue.MAJOR.equals(sonarSeverity)) {
        return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      } else if (ISonarIssue.INFO.equals(sonarSeverity) || ISonarIssue.MINOR.equals(sonarSeverity)) {
        return ProblemHighlightType.WEAK_WARNING;
      } else {
        return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
    }
  }
}
