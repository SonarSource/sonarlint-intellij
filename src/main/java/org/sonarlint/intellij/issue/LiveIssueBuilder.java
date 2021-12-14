/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
 * sonarlint@sonarsource.com
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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.sonarlint.intellij.issue.LocationKt.resolvedLocation;
import static org.sonarlint.intellij.issue.QuickFixKt.convert;

public class LiveIssueBuilder {
  private final IssueMatcher matcher;
  private final Project myProject;

  public LiveIssueBuilder(Project project) {
    this.matcher = new IssueMatcher(project);
    this.myProject = project;
  }

  public LiveIssue buildLiveIssue(Issue issue, ClientInputFile inputFile) throws IssueMatcher.NoMatchException {
    return ReadAction.compute(() -> {
      var psiFile = matcher.findFile(inputFile.getClientObject());
      var textRange = issue.getTextRange();
      var quickFixes = issue.quickFixes()
        .stream().map(fix -> convert(myProject, fix))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      if (textRange != null) {
        var rangeMarker = matcher.match(psiFile, textRange);
        var context = transformFlows(psiFile, issue.flows(), issue.getRuleKey());
        return new LiveIssue(issue, psiFile, rangeMarker, context.orElse(null), quickFixes);
      } else {
        return new LiveIssue(issue, psiFile, quickFixes);
      }
    });
  }

  private Optional<IssueContext> transformFlows(PsiFile psiFile, List<org.sonarsource.sonarlint.core.analysis.api.Flow> flows, String rule) {
    List<Flow> matchedFlows = new LinkedList<>();

    for (var flow : flows) {
      List<Location> matchedLocations = new LinkedList<>();
      for (var loc : flow.locations()) {
        try {
          var textRange = loc.getTextRange();
          var locInputFile = loc.getInputFile();
          if (textRange != null && locInputFile != null) {
            var locPsiFile = matcher.findFile(locInputFile.getClientObject());
            var range = matcher.match(locPsiFile, textRange);
            matchedLocations.add(resolvedLocation(locPsiFile.getVirtualFile(), range, loc.getMessage(), null));
          }
        } catch (IssueMatcher.NoMatchException e) {
          // File content is likely to have changed during the analysis, should be fixed in next analysis
          SonarLintConsole.get(myProject)
            .debug("Failed to find secondary location of issue for file: '" + psiFile.getName() + "'. The location won't be displayed - " + e.getMessage());
        } catch (Exception e) {
          var detailString = String.join(",",
            rule,
            String.valueOf(loc.getStartLine()),
            String.valueOf(loc.getStartLineOffset()),
            String.valueOf(loc.getEndLine()),
            String.valueOf(loc.getEndLineOffset())
          );
          SonarLintConsole.get(myProject).error("Error finding secondary location for issue: " + detailString, e);
          return Optional.empty();
        }
      }
      var matchedFlow = new Flow(matchedLocations);
      matchedFlows.add(matchedFlow);

    }

    return MatchedFlowsAdapter.adapt(matchedFlows);
  }
}
