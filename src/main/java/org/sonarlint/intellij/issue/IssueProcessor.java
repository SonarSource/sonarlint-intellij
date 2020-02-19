/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import com.intellij.openapi.components.AbstractProjectComponent;
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
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintJob;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueLocation;

import static java.util.stream.Collectors.toList;

public class IssueProcessor extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(IssueProcessor.class);
  private final IssueMatcher matcher;
  private final IssueManager manager;
  private final SonarLintConsole console;
  private final ServerIssueUpdater serverIssueUpdater;

  public IssueProcessor(Project project, IssueMatcher matcher, IssueManager manager, ServerIssueUpdater serverIssueUpdater) {
    super(project);
    this.matcher = matcher;
    this.manager = manager;
    this.console = SonarLintConsole.get(project);
    this.serverIssueUpdater = serverIssueUpdater;
  }

  public void process(final SonarLintJob job, ProgressIndicator indicator, final Collection<Issue> rawIssues, Collection<ClientInputFile> failedAnalysisFiles) {
    long start = System.currentTimeMillis();
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
        serverIssueUpdater.fetchAndMatchServerIssues(filesWithIssuesPerModule, indicator, job.waitForServerIssues());
      }
    }

    AnalysisCallback callback = job.callback();
    if (callback != null) {
      callback.onSuccess(failedVirtualFiles);
    }
  }

  private static Set<VirtualFile> asVirtualFiles(Collection<ClientInputFile> failedAnalysisFiles) {
    return failedAnalysisFiles.stream().map(f -> (VirtualFile) f.getClientObject()).collect(Collectors.toSet());
  }

  private void logFoundIssuesIfAny(Collection<Issue> rawIssues, long start, Map<VirtualFile, Collection<LiveIssue>> transformedIssues) {
    String issueStr = SonarLintUtils.pluralize("issue", rawIssues.size());
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
        console.info("File won't be refreshed because there were errors during analysis: " + f.getPath());
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
        console.debug("Failed to find location of issue for file: '" + vFile.getName() + "'. The file won't be refreshed - " + e.getMessage());
        map.remove(vFile);
      } catch (Exception e) {
        LOGGER.error("Error finding location for issue", e);
      }
    }

    return map;
  }

  private LiveIssue transformIssue(Issue issue, ClientInputFile inputFile) throws IssueMatcher.NoMatchException {
    PsiFile psiFile = matcher.findFile(inputFile.getClientObject());
    if (issue.getStartLine() != null) {
      RangeMarker rangeMarker = matcher.match(psiFile, issue);
      List<LiveIssue.Flow> flows = transformFlows(psiFile, issue.flows(), issue.getRuleKey());
      return new LiveIssue(issue, psiFile, rangeMarker, flows);
    } else {
      return new LiveIssue(issue, psiFile);
    }
  }

  private List<LiveIssue.Flow> transformFlows(PsiFile psiFile, List<Issue.Flow> flows, String rule) {
    List<LiveIssue.Flow> transformedFlows = new LinkedList<>();

    for (Issue.Flow f : flows) {
      List<LiveIssue.IssueLocation> newLocations = new LinkedList<>();
      for (IssueLocation loc : f.locations()) {
        try {
          RangeMarker range = matcher.match(psiFile, loc);
          newLocations.add(new LiveIssue.IssueLocation(range, loc.getMessage()));
        } catch (IssueMatcher.NoMatchException e) {
          // File content is likely to have changed during the analysis, should be fixed in next analysis
          console.debug("Failed to find secondary location of issue for file: '" + psiFile.getName() + "'. The location won't be displayed - " + e.getMessage());
        } catch (Exception e) {
          LOGGER.error("Error finding secondary location for issue", e, rule,
            String.valueOf(loc.getStartLine()), String.valueOf(loc.getStartLineOffset()), String.valueOf(loc.getEndLine()), String.valueOf(loc.getEndLineOffset()));
          return Collections.emptyList();
        }
      }
      LiveIssue.Flow newFlow = new LiveIssue.Flow(newLocations);
      transformedFlows.add(newFlow);

    }

    return transformedFlows;
  }
}
