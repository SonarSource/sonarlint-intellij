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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.model.ISonarIssue;
import org.sonar.ide.intellij.util.SonarQubeBundle;

public class SonarQubeGlobalInspection extends GlobalSimpleInspectionTool {

  private static final Logger LOG = Logger.getInstance(SonarQubeGlobalInspection.class);

  private GlobalInspectionContext globalContext;
  private InspectionManager manager;
  private ProblemDescriptionsProcessor problemDescriptionsProcessor;


  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "SonarQube Issue";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "SonarQubeIssue";
  }


  @NotNull
  @Override
  public String getStaticDescription() {
    return SonarQubeBundle.message("sonarqube.inspection.description");
  }

  @Override
  public void checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext, @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    this.globalContext = globalContext;
    this.manager = manager;
    this.problemDescriptionsProcessor = problemDescriptionsProcessor;
    final SonarQubeInspectionContext sonarQubeInspectionContext = globalContext.getExtension(SonarQubeInspectionContext.KEY);
    if (sonarQubeInspectionContext == null) {
      return;
    }

    if (sonarQubeInspectionContext.getRemoteIssuesByFile().containsKey(file) && !sonarQubeInspectionContext.getIssueCache().getModifiedFile().contains(file)) {
      for (final ISonarIssue issue : sonarQubeInspectionContext.getRemoteIssuesByFile().get(file)) {
        createProblem(file, issue);
      }
    }
    if (sonarQubeInspectionContext.getIssueCache().getLocalIssuesByElement().containsKey(file)) {
      for (final IssueOnPsiElement issueOnPsiElement : sonarQubeInspectionContext.getIssueCache().getLocalIssuesByElement().get(file)) {
        issueOnPsiElement.getPsiElement();
        createProblem(file, issueOnPsiElement.getIssue());
      }
    }
  }

  protected void createProblem(@NotNull final PsiFile file, final ISonarIssue issue) {
    if (issue.resolved()) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        try {
          ProblemDescriptor descriptor = computeIssueProblemDescriptor(file, issue, globalContext, manager);
          problemDescriptionsProcessor.addProblemElement(globalContext.getRefManager().getReference(file), descriptor);
        } catch (Exception e) {
          LOG.error("Unable to create problem for issue " + issue.key(), e);
        }
      }
    });
  }

  @Nullable
  protected ProblemDescriptor computeIssueProblemDescriptor(PsiFile psiFile, ISonarIssue issue, GlobalInspectionContext globalContext, InspectionManager manager) {
    PsiElement location = InspectionUtils.getStartElementAtLine(psiFile, issue);
    if (location == null) {
      LOG.warn("Unable to locate issue " + issue.key() + " on file " + psiFile.toString() + " at line " + issue.line());
      location = psiFile;
    }
    return manager.createProblemDescriptor(location,
        InspectionUtils.getProblemMessage(issue),
        new LocalQuickFix[0],
        issue.isNew() ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.WEAK_WARNING,
        false,
        false
    );
  }

}
