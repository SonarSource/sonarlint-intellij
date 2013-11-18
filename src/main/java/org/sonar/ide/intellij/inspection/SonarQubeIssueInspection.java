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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.model.ISonarIssue;

import java.util.Map;

public class SonarQubeIssueInspection extends AbstractSonarQubeInspection {

  private static final Logger LOG = Logger.getInstance(SonarQubeIssueInspection.class);

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

  @Override
  public void runInspection(AnalysisScope scope, final InspectionManager manager, final GlobalInspectionContext globalContext, final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final SonarQubeInspectionContext sonarQubeInspectionContext = globalContext.getExtension(SonarQubeInspectionContext.KEY);
    if (sonarQubeInspectionContext == null) {
      return;
    }
    final Project p = globalContext.getProject();
    final ProjectSettings projectSettings = p.getComponent(ProjectSettings.class);
    final Map<String, PsiFile> resourceCache = sonarQubeInspectionContext.getResourceCache(globalContext, p, projectSettings);

    for (final ISonarIssue issue : sonarQubeInspectionContext.getRemoteIssues()) {
      if (sonarQubeInspectionContext.getModifiedFileKeys().contains(issue.resourceKey())) {
        // Don't create issue for resources that are locally modified as they will be populated by local issues
        continue;
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

    for (final ISonarIssue issue : sonarQubeInspectionContext.getLocalIssues()) {
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


}
