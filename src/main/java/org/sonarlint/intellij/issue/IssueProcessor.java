/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintJob;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IssueProcessor extends AbstractProjectComponent {
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
    Map<VirtualFile, Collection<LiveIssue>> transformedIssues;
    long start = System.currentTimeMillis();
    AccessToken token = ReadAction.start();
    try {
      transformedIssues = transformIssues(rawIssues, job.files(), failedAnalysisFiles);

      // this might be updated later after tracking with server issues
      manager.store(transformedIssues);

    } finally {
      // closeable only introduced in 2016.2
      token.finish();
    }

    String issueStr = rawIssues.size() == 1 ? "issue" : "issues";
    console.debug(String.format("Processed %d %s in %d ms", rawIssues.size(), issueStr, System.currentTimeMillis() - start));

    long issuesToShow = transformedIssues.entrySet().stream()
      .mapToLong(e -> e.getValue().size())
      .sum();

    Collection<VirtualFile> filesWithIssues = transformedIssues.entrySet().stream()
      .filter(e -> !e.getValue().isEmpty())
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());

    String end = issuesToShow == 1 ? " issue" : " issues";
    console.info("Found " + issuesToShow + end);

    if (!filesWithIssues.isEmpty() && shouldUpdateServerIssues(job.trigger())) {
      serverIssueUpdater.fetchAndMatchServerIssues(filesWithIssues, indicator);
    }

    AnalysisCallback callback = job.callback();
    if (callback != null) {
      callback.onSuccess(transformedIssues);
    }
  }

  private static boolean shouldUpdateServerIssues(TriggerType trigger) {
    switch (trigger) {
      case ACTION:
      case BINDING_CHANGE:
      case BINDING_UPDATE:
      case CHECK_IN:
      case EDITOR_OPEN:
        return true;
      default:
        return false;
    }
  }

  private Map<VirtualFile, Collection<LiveIssue>> removeFailedFiles(Collection<VirtualFile> analysed, Collection<ClientInputFile> failedAnalysisFiles) {
    Set<VirtualFile> failedVirtualFiles = failedAnalysisFiles.stream().map(f -> (VirtualFile) f.getClientObject()).collect(Collectors.toSet());
    Map<VirtualFile, Collection<LiveIssue>> map = new HashMap<>();

    for (VirtualFile f : analysed) {
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
  private Map<VirtualFile, Collection<LiveIssue>> transformIssues(
    Collection<Issue> issues, Collection<VirtualFile> analysed, Collection<ClientInputFile> failedAnalysisFiles) {

    Map<VirtualFile, Collection<LiveIssue>> map = removeFailedFiles(analysed, failedAnalysisFiles);

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
        LiveIssue toStore = transformIssue(issue, vFile);
        map.get(vFile).add(toStore);
      } catch (IssueMatcher.NoMatchException e) {
        console.error("Failed to find location of issue for file: '" + vFile.getName() + "'. The file won't be refreshed - " + e.getMessage());
        map.remove(vFile);
      }
    }

    return map;
  }

  private LiveIssue transformIssue(Issue issue, VirtualFile vFile) throws IssueMatcher.NoMatchException {
    PsiFile psiFile = matcher.findFile(vFile);
    if (issue.getStartLine() != null) {
      RangeMarker rangeMarker = matcher.match(psiFile, issue);
      return new LiveIssue(issue, psiFile, rangeMarker);
    } else {
      return new LiveIssue(issue, psiFile);
    }
  }
}
