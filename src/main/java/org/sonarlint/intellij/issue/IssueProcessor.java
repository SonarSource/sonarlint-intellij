/**
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IssueProcessor extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(IssueProcessor.class);
  private final IssueMatcher matcher;
  private final DaemonCodeAnalyzer codeAnalyzer;
  private final IssueStore store;
  private final SonarLintConsole console;
  private final ServerIssueUpdater serverIssueUpdater;

  public IssueProcessor(Project project, IssueMatcher matcher, DaemonCodeAnalyzer codeAnalyzer, IssueStore store, ServerIssueUpdater serverIssueUpdater) {
    super(project);
    this.matcher = matcher;
    this.codeAnalyzer = codeAnalyzer;
    this.store = store;
    this.console = SonarLintConsole.get(project);
    this.serverIssueUpdater = serverIssueUpdater;
  }

  public void process(final SonarLintJobManager.SonarLintJob job, final Collection<Issue> issues, Collection<ClientInputFile> failedAnalysisFiles, TriggerType trigger) {
    Map<VirtualFile, Collection<LocalIssuePointer>> map;
    long start = System.currentTimeMillis();
    AccessToken token = ReadAction.start();
    try {
      map = transformIssues(issues, job.files(), failedAnalysisFiles);

      Set<VirtualFile> notAnalyzed =
        job.files().stream().filter(store::containsFile).collect(Collectors.toSet());

      store.store(map);

      for (VirtualFile file : job.files()) {
        if (shouldTrackServerIssues(file, trigger, notAnalyzed)) {
          serverIssueUpdater.fetchAndMatchServerIssues(file);
        }
      }

      // restart analyzer for all files analyzed (even the ones without issues) so that our external annotator is called
      getPsi(job.files()).forEach(codeAnalyzer::restart);
    } finally {
      // closeable only introduced in 2016.2
      token.finish();
    }

    console.debug("Stored matched issues in " + (System.currentTimeMillis() - start) + " ms");

    String end;
    if (issues.size() == 1) {
      end = " issue";
    } else {
      end = " issues";
    }

    console.info("Found " + issues.size() + end);
  }

  private boolean shouldTrackServerIssues(VirtualFile file, TriggerType trigger, Set<VirtualFile> notAnalyzed) {
    return trigger == TriggerType.EDITOR_OPEN
      || trigger == TriggerType.ACTION
      || !store.getForFile(file).isEmpty() && notAnalyzed.contains(file);
  }

  /**
   * Transforms issues and organizes them per file
   */
  private Map<VirtualFile, Collection<LocalIssuePointer>> transformIssues(
    Collection<Issue> issues, Collection<VirtualFile> analysed, Collection<ClientInputFile> failedAnalysisFiles) {

    Map<VirtualFile, Collection<LocalIssuePointer>> map = new HashMap<>();
    Set<VirtualFile> failedVirtualFiles = failedAnalysisFiles.stream().map(f -> (VirtualFile) f.getClientObject()).collect(Collectors.toSet());

    for(VirtualFile f : analysed) {
      if(failedVirtualFiles.contains(f)) {
        console.info("File won't be refreshed because there were errors during analysis: " + f.getPath());
      } else {
        // it's important to store all files, even without issues, to correctly track the leak period (SLI-86)
        map.put(f, new ArrayList<>());
      }
    }

    for (Issue i : issues) {
      ClientInputFile inputFile = i.getInputFile();
      if (inputFile == null || inputFile.getPath() == null || failedAnalysisFiles.contains(inputFile)) {
        // ignore project level issues and files that had errors
        continue;
      }
      try {
        VirtualFile vFile = inputFile.getClientObject();
        if (!vFile.isValid()) {
          // file might have been deleted meanwhile
          continue;
        }
        PsiFile psiFile = matcher.findFile(vFile);
        LocalIssuePointer toStore = matcher.match(psiFile, i);
        map.get(psiFile.getVirtualFile()).add(toStore);
      } catch (IssueMatcher.NoMatchException e) {
        console.error("Failed to find location of issue", e);
      }
    }

    return map;
  }

  private Collection<PsiFile> getPsi(Collection<VirtualFile> files) {
    List<PsiFile> psiFiles = new LinkedList<>();

    for (VirtualFile f : files) {
      if (!f.isValid()) {
        continue;
      }
      try {
        psiFiles.add(matcher.findFile(f));
      } catch (IssueMatcher.NoMatchException e) {
        LOGGER.error("Couldn't find PSI for file: " + f.getPath(), e);
      }
    }
    return psiFiles;
  }
}
