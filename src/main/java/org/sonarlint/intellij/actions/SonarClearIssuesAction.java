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
package org.sonarlint.intellij.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarClearIssuesAction extends AnAction {
  private static final Logger LOGGER = Logger.getInstance(SonarClearIssuesAction.class);

  public SonarClearIssuesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (project != null) {
      IssueManager issueManager = SonarLintUtils.get(project, IssueManager.class);
      DaemonCodeAnalyzer codeAnalyzer = SonarLintUtils.get(project, DaemonCodeAnalyzer.class);

      ApplicationManager.getApplication().runReadAction(() -> {
        issueManager.clear();

        // run annotator to remove highlighting of issues
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        VirtualFile[] openFiles = editorManager.getOpenFiles();
        Collection<PsiFile> psiFiles = findFiles(project, openFiles);
        psiFiles.forEach(codeAnalyzer::restart);
      });
    }
  }

  public Collection<PsiFile> findFiles(Project project, VirtualFile[] files) {
    PsiManager psiManager = PsiManager.getInstance(project);
    List<PsiFile> psiFiles = new ArrayList<>(files.length);

    for (VirtualFile vFile : files) {
      if (!vFile.isValid()) {
        continue;
      }
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
