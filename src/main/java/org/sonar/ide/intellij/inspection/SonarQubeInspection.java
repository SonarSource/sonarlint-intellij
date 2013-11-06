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

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.model.ISonarIssue;

import java.util.HashMap;
import java.util.Map;

public class SonarQubeInspection extends GlobalInspectionTool {

  private static final Logger LOG = Logger.getInstance(SonarQubeInspection.class);

  private static final char DELIMITER = ':';
  private static final char PACKAGE_DELIMITER = '.';
  public static final String DEFAULT_PACKAGE_NAME = "[default]";

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "SonarQube";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "sonarqube";
  }

  @Override
  public void runInspection(AnalysisScope scope, final InspectionManager manager, final GlobalInspectionContext globalContext, final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final SonarQubeInspectionContext sonarQubeInspectionContext = globalContext.getExtension(SonarQubeInspectionContext.KEY);
    if (sonarQubeInspectionContext == null) {
      return;
    }
    final Project p = globalContext.getProject();
    ModuleManager moduleManager = ModuleManager.getInstance(p);
    final ProjectSettings projectSettings = p.getComponent(ProjectSettings.class);
    final String moduleKey = projectSettings.getProjectKey();
    final Map<String, PsiFile> resourceCache = new HashMap<String, PsiFile>();
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

    for (final ISonarIssue issue : sonarQubeInspectionContext.getIssues()) {
      final PsiFile psiFile = resourceCache.get(issue.resourceKey());
      if (psiFile != null) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            ProblemDescriptor descriptor = computeIssueProblemDescriptor(psiFile, issue, globalContext, manager);
            problemDescriptionsProcessor.addProblemElement(globalContext.getRefManager().getReference(psiFile), descriptor);
          }
        });
      }
    }

    super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor);
  }

  @Nullable
  private ProblemDescriptor computeIssueProblemDescriptor(PsiFile psiFile, ISonarIssue issue, GlobalInspectionContext globalContext, InspectionManager manager) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(globalContext.getProject());
    Document document = documentManager.getDocument(psiFile.getContainingFile());
    if (document == null) {
      return null;
    }
    TextRange range = getTextRange(document, issue.line());
    return manager.createProblemDescriptor(psiFile, range,
        issue.message(),
        issueToProblemHighlightType(issue),
        false
    );
  }

  @NotNull
  protected TextRange getTextRange(@NotNull Document document, int line) {
    int lineStartOffset = document.getLineStartOffset(line - 1);
    int lineEndOffset = document.getLineEndOffset(line - 1);
    return new TextRange(lineStartOffset, lineEndOffset);
  }

  public String getComponentKey(String moduleKey, PsiFile file) {
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

  public String getJavaComponentKey(String moduleKey, PsiJavaFile file) {
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

  public ProblemHighlightType issueToProblemHighlightType(ISonarIssue issue) {
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
