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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.sonar.runner.api.Issue;
import org.sonarlint.intellij.analysis.SonarlintAnalyzer;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class IssueProcessor extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(IssueProcessor.class);
  private final IssueMatcher matcher;
  private final IssueStore store;
  private final DaemonCodeAnalyzer codeAnalyzer;
  private final SonarLintConsole console;

  protected IssueProcessor(Project project, IssueMatcher matcher, IssueStore store, DaemonCodeAnalyzer codeAnalyzer) {
    super(project);
    this.matcher = matcher;
    this.store = store;
    this.codeAnalyzer = codeAnalyzer;
    this.console = SonarLintConsole.getSonarQubeConsole(project);
  }

  public void process(final SonarlintAnalyzer.SonarLintJob job, final Collection<Issue> issues) {
    Multimap<PsiFile, IssueStore.StoredIssue> map;
    final VirtualFile moduleBaseDir = SonarLintUtils.getModuleRoot(job.module());

    long start = System.currentTimeMillis();
    AccessToken token = ReadAction.start();
    try {
      Collection<PsiFile> psiFiles = getPsi(job.files());
      clearFiles(psiFiles);
      map = transformIssues(moduleBaseDir, issues);

      for (PsiFile file : map.keySet()) {
        store.store(file, map.get(file));
      }

      // restart analyzer for all files analyzed (even the ones without issues) so that our external annotator is called
      for(PsiFile f : psiFiles) {
        codeAnalyzer.restart(f);
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


  console.clear();
    console.info("Found " + issues.size() + end);

  }

  /**
   * Transforms issues and organizes them per-PsiFile
   */
  private Multimap<PsiFile, IssueStore.StoredIssue> transformIssues(VirtualFile moduleBaseDir, Collection<Issue> issues) {
    Multimap<PsiFile, IssueStore.StoredIssue> map = HashMultimap.create();

    for (Issue i : issues) {
      try {
        PsiFile psiFile = matcher.findFile(moduleBaseDir, i);
        IssueStore.StoredIssue toStore = matcher.match(psiFile, i);
        map.put(psiFile, toStore);
      } catch (IssueMatcher.NoMatchException e) {
        console.error("Failed to find location of issue", e);
      }
    }

    return map;
  }

  private Collection<PsiFile> getPsi(Collection<VirtualFile> files) {
    List<PsiFile> psiFiles = new LinkedList<>();

    for(VirtualFile f : files) {
      try {
      psiFiles.add(matcher.findFile(f));
      } catch (IssueMatcher.NoMatchException e) {
        LOGGER.error("Couldn't find PSI for file: " + f.getPath(), e);
      }
    }
    return psiFiles;
  }

  /**
   * Clears all files analyzed (including the ones without issues)
   */
  private void clearFiles(Collection<PsiFile> files) {
    for (PsiFile psiFile : files) {
        store.clearFile(psiFile);
    }
  }

}
