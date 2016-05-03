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
package org.sonarlint.intellij.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sonarlint.intellij.issue.IssueMatcher;
import org.sonarlint.intellij.issue.IssueStore;

public class SonarClearIssues extends AnAction {
  private static final Logger LOGGER = Logger.getInstance(SonarClearIssues.class);

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();

    if (project != null) {
      IssueStore store = project.getComponent(IssueStore.class);
      DaemonCodeAnalyzer codeAnalyzer = project.getComponent(DaemonCodeAnalyzer.class);

      Set<VirtualFile> files = new HashSet<>(store.getAll().keySet());
      AccessToken token = ReadAction.start();
      try {
        store.clear();

        // run annotator to remove highlighting of issues
        for (PsiFile psiFile : findFiles(project, files)) {
          codeAnalyzer.restart(psiFile);
        }
      } finally {
        token.finish();
      }
    }
  }

  public Collection<PsiFile> findFiles(Project project, Collection<VirtualFile> files) {
    PsiManager psiManager = PsiManager.getInstance(project);
    List<PsiFile> psiFiles = new ArrayList<>(files.size());

    for (VirtualFile vFile : files) {
      PsiFile psiFile = psiManager.findFile(vFile);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      } else {
        LOGGER.warn("Couldn't find PSI for file: " + vFile.getPath());
      }
    }
    return psiFiles;
  }
}
