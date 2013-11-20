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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.model.ISonarIssue;
import org.sonar.wsclient.jsonsimple.JSONArray;
import org.sonar.wsclient.jsonsimple.JSONObject;
import org.sonar.wsclient.jsonsimple.JSONValue;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractSonarQubeInspection extends GlobalInspectionTool {

  private static final Logger LOG = Logger.getInstance(AbstractSonarQubeInspection.class);

  private Map<String, PsiFile> resourceCache;
  private GlobalInspectionContext globalContext;
  private InspectionManager manager;
  private ProblemDescriptionsProcessor problemDescriptionsProcessor;

  @Override
  public void runInspection(AnalysisScope scope, final InspectionManager manager, final GlobalInspectionContext globalContext, final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    this.globalContext = globalContext;
    this.manager = manager;
    this.problemDescriptionsProcessor = problemDescriptionsProcessor;
    final SonarQubeInspectionContext sonarQubeInspectionContext = globalContext.getExtension(SonarQubeInspectionContext.KEY);
    if (sonarQubeInspectionContext == null) {
      return;
    }
    final Project p = globalContext.getProject();
    final ProjectSettings projectSettings = p.getComponent(ProjectSettings.class);
    resourceCache = sonarQubeInspectionContext.getResourceCache(globalContext, p, projectSettings);

    populateProblems(sonarQubeInspectionContext);

    super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor);
  }

  protected void createProblem(final ISonarIssue issue) {
    if (issue.resolved()) {
      return;
    }
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

  protected abstract void populateProblems(SonarQubeInspectionContext sonarQubeInspectionContext);

  @Nullable
  protected ProblemDescriptor computeIssueProblemDescriptor(PsiFile psiFile, ISonarIssue issue, GlobalInspectionContext globalContext, InspectionManager manager) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(globalContext.getProject());
    Document document = documentManager.getDocument(psiFile.getContainingFile());
    if (document == null) {
      return null;
    }
    Integer line = issue.line();
    TextRange range = getTextRange(document, line != null ? line : 1);
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
