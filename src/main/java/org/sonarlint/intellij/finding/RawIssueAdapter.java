/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.finding;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.util.VirtualFileUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueFlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;
import static org.sonarlint.intellij.finding.LocationKt.resolvedLocation;
import static org.sonarlint.intellij.finding.QuickFixKt.convert;
import static org.sonarlint.intellij.util.ProjectUtils.toPsiFile;

public class RawIssueAdapter {

  public static LiveSecurityHotspot toLiveSecurityHotspot(Module module, RaisedHotspotDto rawHotspot,
    VirtualFile virtualFile, @Nullable Long modificationStamp) {
    return computeReadActionSafely(module, () -> {
      var project = module.getProject();
      var matcher = new TextRangeMatcher(project);
      var psiFile = toPsiFile(project, virtualFile);
      var textRange = rawHotspot.getTextRange();
      var quickFixes = transformQuickFixes(project, rawHotspot.getQuickFixes(), modificationStamp);
      if (textRange != null) {
        var rangeMarker = matcher.match(psiFile, textRange);
        var context = transformFlows(project, matcher, psiFile, rawHotspot.getFlows(), rawHotspot.getRuleKey());
        return new LiveSecurityHotspot(module, rawHotspot, virtualFile, rangeMarker, context.orElse(null), quickFixes);
      } else {
        return new LiveSecurityHotspot(module, rawHotspot, virtualFile, quickFixes);
      }
    });
  }

  @Nullable
  public static LiveIssue toLiveIssue(Module module, RaisedIssueDto rawIssue,
    VirtualFile virtualFile, @Nullable Long modificationStamp) {
    return computeReadActionSafely(module, () -> {
      var project = module.getProject();
      var matcher = new TextRangeMatcher(project);
      var psiFile = toPsiFile(project, virtualFile);
      var textRange = rawIssue.getTextRange();
      var quickFixes = transformQuickFixes(project, rawIssue.getQuickFixes(), modificationStamp);
      if (textRange != null) {
        var rangeMarker = matcher.match(psiFile, textRange);
        var context = transformFlows(project, matcher, psiFile, rawIssue.getFlows(), rawIssue.getRuleKey());
        return new LiveIssue(module, rawIssue, virtualFile, rangeMarker, context.orElse(null), quickFixes);
      } else {
        return new LiveIssue(module, rawIssue, virtualFile, quickFixes);
      }
    });
  }

  private static Optional<FindingContext> transformFlows(Project project, TextRangeMatcher matcher, PsiFile psiFile,
    List<IssueFlowDto> flows, String rule) {
    List<Flow> matchedFlows = new LinkedList<>();

    for (var i = 0; i < flows.size(); i++) {
      var flow = flows.get(i);
      List<Location> matchedLocations = new LinkedList<>();
      for (var loc : flow.getLocations()) {
        try {
          var textRange = loc.getTextRange();
          var fileUri = loc.getFileUri();
          if (fileUri == null) {
            continue;
          }
          VirtualFile locVirtualFile = null;
          var locFileUri = loc.getFileUri();
          if (locFileUri != null) {
            locVirtualFile = VirtualFileUtils.INSTANCE.uriToVirtualFile(fileUri);
          }
          if (textRange != null && locVirtualFile != null) {
            var locPsiFile = toPsiFile(project, locVirtualFile);
            var range = matcher.match(locPsiFile, textRange);
            matchedLocations.add(resolvedLocation(locPsiFile.getVirtualFile(), range, loc.getMessage(), null));
          }
        } catch (TextRangeMatcher.NoMatchException e) {
          // File content is likely to have changed during the analysis, should be fixed in next analysis
          SonarLintConsole.get(project)
            .debug("Failed to find secondary location of finding for file: '" + psiFile.getName() + "'. The location won't be displayed - " + e.getMessage());
        } catch (Exception e) {
          var textRange = loc.getTextRange();
          var detailString = String.join(",",
            rule,
            String.valueOf(textRange == null ? null : textRange.getStartLine()),
            String.valueOf(textRange == null ? null : textRange.getStartLineOffset()),
            String.valueOf(textRange == null ? null : textRange.getEndLine()),
            String.valueOf(textRange == null ? null : textRange.getEndLineOffset()));
          SonarLintConsole.get(project).error("Error finding secondary location for finding: " + detailString, e);
          return Optional.empty();
        }
      }
      var matchedFlow = new Flow(i + 1, matchedLocations);
      matchedFlows.add(matchedFlow);

    }

    return adapt(matchedFlows);
  }

  public static Optional<FindingContext> adapt(List<Flow> flows) {
    return flows.isEmpty()
      ? Optional.empty()
      : Optional.of(new FindingContext(adaptFlows(flows)));
  }

  private static List<Flow> adaptFlows(List<Flow> flows) {
    return flows.stream().anyMatch(Flow::hasMoreThanOneLocation)
      ? reverse(flows)
      : List.of(groupToSingleFlow(flows));
  }

  private static Flow groupToSingleFlow(List<Flow> flows) {
    return new Flow(1, flows.stream()
      .flatMap(f -> f.getLocations().stream())
      .sorted(Comparator.comparing(i -> i.getRange().getStartOffset()))
      .toList());
  }

  private static List<Flow> reverse(List<Flow> flows) {
    List<Flow> list = new ArrayList<>();
    for (var i = 0; i < flows.size(); i++) {
      var flow = flows.get(i);
      var reorderedLocations = new ArrayList<>(flow.getLocations());
      Collections.reverse(reorderedLocations);
      var apply = new Flow(i + 1, reorderedLocations);
      list.add(apply);
    }
    return list;
  }

  private static List<QuickFix> transformQuickFixes(Project project,
    List<QuickFixDto> quickFixes, @Nullable Long modificationStamp) {
    return quickFixes
      .stream().map(fix -> convert(project, fix, modificationStamp))
      .filter(Objects::nonNull)
      .toList();
  }

  private RawIssueAdapter() {
    // utility class
  }
}
