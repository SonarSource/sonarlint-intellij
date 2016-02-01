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
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;
import org.sonarlint.intellij.analysis.SonarLintAnalyzer;
import org.sonarlint.intellij.messages.AnalysisResultsListener;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.IssueListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class IssueProcessor extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(IssueProcessor.class);
  private final IssueMatcher matcher;
  private final DaemonCodeAnalyzer codeAnalyzer;
  private final SonarLintConsole console;
  private final MessageBus messageBus;

  public IssueProcessor(Project project, IssueMatcher matcher, DaemonCodeAnalyzer codeAnalyzer) {
    super(project);
    this.matcher = matcher;
    this.codeAnalyzer = codeAnalyzer;
    this.console = SonarLintConsole.get(project);
    this.messageBus = project.getMessageBus();
  }

  public void process(final SonarLintAnalyzer.SonarLintJob job, final Collection<IssueListener.Issue> issues) {
    Map<VirtualFile, Collection<IssuePointer>> map;
    final VirtualFile moduleBaseDir = SonarLintUtils.getModuleRoot(job.module());

    long start = System.currentTimeMillis();
    AccessToken token = ReadAction.start();
    try {
      map = transformIssues(moduleBaseDir, issues, job.files());
      messageBus.syncPublisher(AnalysisResultsListener.SONARLINT_ANALYSIS_DONE_TOPIC).analysisDone(map);

      // restart analyzer for all files analyzed (even the ones without issues) so that our external annotator is called
      for (PsiFile psiFile : getPsi(job.files())) {
        codeAnalyzer.restart(psiFile);
      }
    } finally {
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

  /**
   * Transforms issues and organizes them per file
   */
  private Map<VirtualFile, Collection<IssuePointer>> transformIssues(VirtualFile moduleBaseDir, Collection<IssueListener.Issue> issues, Collection<VirtualFile> analysed) {
    Map<VirtualFile, Collection<IssuePointer>> map = new HashMap<>();

    for(VirtualFile f : analysed) {
      map.put(f, new ArrayList<IssuePointer>());
    }

    for (IssueListener.Issue i : issues) {
      try {
        PsiFile psiFile = matcher.findFile(moduleBaseDir.getFileSystem(), i);
        IssuePointer toStore = matcher.match(psiFile, i);
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
      try {
        psiFiles.add(matcher.findFile(f));
      } catch (IssueMatcher.NoMatchException e) {
        LOGGER.error("Couldn't find PSI for file: " + f.getPath(), e);
      }
    }
    return psiFiles;
  }
}
