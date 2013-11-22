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
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
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

public class SonarQubeExternalAnnotator extends ExternalAnnotator<SonarQubeExternalAnnotator.State, SonarQubeExternalAnnotator.State> {

  private static final Logger LOG = Logger.getInstance(SonarQubeExternalAnnotator.class);

  private SonarQubeServer server;
  private ProjectSettings projectSettings;
  private SonarQubeConsole console;

  public static class State {
    public PsiJavaFile file;
    public VirtualFile vfile;
    public Project project;
    public List<ISonarIssue> remoteIssues;
    public String componentKey;
  }

  @Nullable
  @Override
  public State collectInformation(@NotNull PsiFile file, @NotNull Editor editor) {
    if (!(file instanceof PsiJavaFile)) {
      return null;
    }
    VirtualFile vfile = file.getVirtualFile();
    if (vfile == null) {
      return null;
    }
    State result = new State();
    result.file = (PsiJavaFile) file;
    result.vfile = vfile;
    result.project = file.getProject();
    return result;
  }

  @Nullable
  @Override
  public State doAnnotate(State collectedInfo) {
    final Project p = collectedInfo.project;
    console = SonarQubeConsole.getSonarQubeConsole(p);
    projectSettings = p.getComponent(ProjectSettings.class);
    String serverId = projectSettings.getServerId();
    if (serverId == null) {
      console.error("Project is not associated to SonarQube");
      return null;
    }
    SonarQubeSettings settings = SonarQubeSettings.getInstance();
    server = settings.getServer(serverId);
    if (server == null) {
      console.error("Project was associated to a server that is not configured: " + serverId);
      return null;
    }
    VirtualFile virtualFile = collectedInfo.vfile;
    if (virtualFile == null) {
      return null;
    }
    Module module = ProjectRootManager.getInstance(collectedInfo.file.getProject()).getFileIndex().getModuleForFile(virtualFile);
    if (module == null) {
      return null;
    }
    String sonarKeyOfModule = projectSettings.getModuleKeys().get(module.getName());
    if (sonarKeyOfModule == null) {
      console.error("Module " + module.getName() + " is not associated to SonarQube");
      return null;
    }
    collectedInfo.componentKey = InspectionUtils.getComponentKey(sonarKeyOfModule, collectedInfo.file);

    SonarQubeIssueCache cache = p.getComponent(SonarQubeIssueCache.class);

    if (!cache.getModifiedFile().contains(collectedInfo.file)) {
      populateRemoteIssues(collectedInfo, collectedInfo.file, console, server, collectedInfo.componentKey);
    }
    return collectedInfo;
  }

  @Override
  public void apply(@NotNull PsiFile file, State annotationResult, @NotNull AnnotationHolder holder) {
    SonarQubeIssueCache cache = annotationResult.project.getComponent(SonarQubeIssueCache.class);
    if (cache.getModifiedFile().contains(annotationResult.file)) {
      for (final ISonarIssue issue : cache.getLocalIssuesByFile().get(file)) {
        createIssueAnnotation(holder, file, issue);
      }
    } else {
      for (final ISonarIssue issue : annotationResult.remoteIssues) {
        createIssueAnnotation(holder, file, issue);
      }
    }
  }

  private void populateRemoteIssues(State state, PsiFile file, SonarQubeConsole console, SonarQubeServer server, String componentKey) {
    try {
      ISonarWSClientFacade sonarClient = WSClientFactory.getInstance().getSonarClient(server);
      state.remoteIssues = sonarClient.getUnresolvedRemoteIssues(componentKey);
    } catch (SonarWSClientException e) {
      LOG.warn("Unable to retrieve remote issues", e);
      console.error("Unable to retrieve remote issues: " + e.getMessage());
      state.remoteIssues = new ArrayList<ISonarIssue>();
    }
  }

  @Nullable
  public static Annotation createIssueAnnotation(AnnotationHolder holder, PsiFile psiFile, ISonarIssue issue) {
    String message = issue.message();
    Integer line = issue.line();
    TextRange range = InspectionUtils.getTextRange(psiFile.getProject(), psiFile, line != null ? line : 1);
    if (issue.isNew()) {
      return holder.createWarningAnnotation(range, "NEW: " + message);
    } else {
      return holder.createWeakWarningAnnotation(range, message);
    }
  }
}
