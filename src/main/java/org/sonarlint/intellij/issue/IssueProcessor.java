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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintJob;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;

import static java.util.stream.Collectors.toList;
import static org.sonarlint.intellij.issue.LocationKt.resolvedLocation;

public class IssueProcessor {
  private static final Logger LOGGER = Logger.getInstance(IssueProcessor.class);
  private final IssueMatcher matcher;
  private final Project myProject;

  public IssueProcessor(Project project) {
    this.matcher = new IssueMatcher(project);
    this.myProject = project;
  }

  public void process(final SonarLintJob job, ProgressIndicator indicator, final Collection<Issue> rawIssues, Collection<ClientInputFile> failedAnalysisFiles) {
    long start = System.currentTimeMillis();
    IssueManager manager = SonarLintUtils.getService(myProject, IssueManager.class);
    Map<VirtualFile, Collection<LiveIssue>> transformedIssues = ReadAction.compute(() -> {
      manager.clear(job.filesToClearIssues());
      Map<VirtualFile, Collection<LiveIssue>> issues = transformIssues(rawIssues, job.allFiles().collect(toList()), failedAnalysisFiles);
      // this might be updated later after tracking with server issues
      manager.store(issues);
      return issues;
    });

    Set<VirtualFile> failedVirtualFiles = asVirtualFiles(failedAnalysisFiles);
    if (!failedVirtualFiles.containsAll(job.allFiles().collect(toList()))) {
      logFoundIssuesIfAny(rawIssues, start, transformedIssues);
    }

    if (shouldUpdateServerIssues(job.trigger())) {
      Map<Module, Collection<VirtualFile>> filesWithIssuesPerModule = new LinkedHashMap<>();

      for (Map.Entry<Module, Collection<VirtualFile>> e : job.filesPerModule().entrySet()) {
        Collection<VirtualFile> moduleFilesWithIssues = e.getValue().stream()
          .filter(f -> !transformedIssues.getOrDefault(f, Collections.emptyList()).isEmpty())
          .collect(toList());
        if (!moduleFilesWithIssues.isEmpty()) {
          filesWithIssuesPerModule.put(e.getKey(), moduleFilesWithIssues);
        }
      }

      if (!filesWithIssuesPerModule.isEmpty()) {
        ServerIssueUpdater serverIssueUpdater = SonarLintUtils.getService(myProject, ServerIssueUpdater.class);
        serverIssueUpdater.fetchAndMatchServerIssues(filesWithIssuesPerModule, indicator, job.waitForServerIssues());
      }
    }

    AnalysisCallback callback = job.callback();
    callback.onSuccess(failedVirtualFiles);
  }

  private static Set<VirtualFile> asVirtualFiles(Collection<ClientInputFile> failedAnalysisFiles) {
    return failedAnalysisFiles.stream().map(f -> (VirtualFile) f.getClientObject()).collect(Collectors.toSet());
  }

  private void logFoundIssuesIfAny(Collection<Issue> rawIssues, long start, Map<VirtualFile, Collection<LiveIssue>> transformedIssues) {
    String issueStr = SonarLintUtils.pluralize("issue", rawIssues.size());
    SonarLintConsole console = SonarLintConsole.get(myProject);
    console.debug(String.format("Processed %d %s in %d ms", rawIssues.size(), issueStr, System.currentTimeMillis() - start));

    long issuesToShow = transformedIssues.values().stream()
      .mapToLong(Collection::size)
      .sum();

    String end = SonarLintUtils.pluralize("issue", issuesToShow);
    console.info("Found " + issuesToShow + " " + end);
  }

  private static boolean shouldUpdateServerIssues(TriggerType trigger) {
    switch (trigger) {
      case ACTION:
      case CONFIG_CHANGE:
      case BINDING_UPDATE:
      case CHECK_IN:
      case EDITOR_OPEN:
        return true;
      default:
        return false;
    }
  }

  private Map<VirtualFile, Collection<LiveIssue>> removeFailedFiles(Collection<VirtualFile> analyzed, Collection<ClientInputFile> failedAnalysisFiles) {
    Map<VirtualFile, Collection<LiveIssue>> map = new HashMap<>();
    Set<VirtualFile> failedVirtualFiles = asVirtualFiles(failedAnalysisFiles);

    for (VirtualFile f : analyzed) {
      if (failedVirtualFiles.contains(f)) {
        SonarLintConsole.get(myProject).info("File won't be refreshed because there were errors during analysis: " + f.getPath());
      } else {
        // it's important to add all files, even without issues, to correctly track the leak period (SLI-86)
        map.put(f, new ArrayList<>());
      }
    }
    return map;
  }

  /**
   * Transforms issues and organizes them per file
   */
  private Map<VirtualFile, Collection<LiveIssue>> transformIssues(Collection<Issue> issues, Collection<VirtualFile> analyzed,
    Collection<ClientInputFile> failedAnalysisFiles) {

    Map<VirtualFile, Collection<LiveIssue>> map = removeFailedFiles(analyzed, failedAnalysisFiles);

    for (Issue issue : issues) {
      ClientInputFile inputFile = issue.getInputFile();
      if (inputFile == null || inputFile.getPath() == null || failedAnalysisFiles.contains(inputFile)) {
        // ignore project level issues and files that had errors
        continue;
      }
      VirtualFile vFile = inputFile.getClientObject();
      if (!vFile.isValid() || !map.containsKey(vFile)) {
        // file is no longer valid (might have been deleted meanwhile) or there has been an error matching an issue in it
        continue;
      }
      try {
        LiveIssue toStore = transformIssue(issue, inputFile);
        map.get(vFile).add(toStore);
      } catch (IssueMatcher.NoMatchException e) {
        // File content is likely to have changed during the analysis, should be fixed in next analysis
        SonarLintConsole.get(myProject).debug("Failed to find location of issue for file: '" + vFile.getName() + "'. The file won't be refreshed - " + e.getMessage());
        map.remove(vFile);
      } catch (Exception e) {
        LOGGER.error("Error finding location for issue", e);
      }
    }

    return map;
  }

  private LiveIssue transformIssue(Issue issue, ClientInputFile inputFile) throws IssueMatcher.NoMatchException {
    PsiFile psiFile = matcher.findFile(inputFile.getClientObject());
    TextRange textRange = issue.getTextRange();
    if (textRange != null) {
      RangeMarker rangeMarker = matcher.match(psiFile, textRange);
      Optional<IssueContext> context = transformFlows(psiFile, issue.flows(), issue.getRuleKey());
      return new LiveIssue(issue, psiFile, rangeMarker, context.orElse(null));
    } else {
      return new LiveIssue(issue, psiFile);
    }
  }

  private Optional<IssueContext> transformFlows(PsiFile psiFile, List<Issue.Flow> flows, String rule) {
    List<Flow> matchedFlows = new LinkedList<>();

    for (Issue.Flow f : flows) {
      List<Location> matchedLocations = new LinkedList<>();
      for (IssueLocation loc : f.locations()) {
        try {
          TextRange textRange = loc.getTextRange();
          if (textRange != null) {
            RangeMarker range = matcher.match(psiFile, textRange);
            matchedLocations.add(resolvedLocation(psiFile.getVirtualFile(), range, loc.getMessage()));
          }
        } catch (IssueMatcher.NoMatchException e) {
          // File content is likely to have changed during the analysis, should be fixed in next analysis
          SonarLintConsole.get(myProject)
            .debug("Failed to find secondary location of issue for file: '" + psiFile.getName() + "'. The location won't be displayed - " + e.getMessage());
        } catch (Exception e) {
          LOGGER.error("Error finding secondary location for issue", e, rule,
            String.valueOf(loc.getStartLine()), String.valueOf(loc.getStartLineOffset()), String.valueOf(loc.getEndLine()), String.valueOf(loc.getEndLineOffset()));
          return Optional.empty();
        }
      }
      Flow matchedFlow = new Flow(matchedLocations);
      matchedFlows.add(matchedFlow);

    }

    return MatchedFlowsAdapter.adapt(matchedFlows);
  }
}
