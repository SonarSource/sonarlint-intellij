/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.Icon;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.runReadActionSafely;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class ClearCurrentFileIssuesAction extends AbstractSonarAction {

  public ClearCurrentFileIssuesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    var project = e.getProject();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (project != null) {
      var codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);

      runReadActionSafely(project, () -> {
        getService(project, AnalysisSubmitter.class).getOnTheFlyFindingsHolder().clearCurrentFile();

        // run annotator to remove highlighting of issues
        var editorManager = FileEditorManager.getInstance(project);
        var openFiles = editorManager.getOpenFiles();
        var psiFiles = findFiles(project, openFiles);
        psiFiles.forEach(codeAnalyzer::restart);
      });
    }
  }

  public Collection<PsiFile> findFiles(Project project, VirtualFile[] files) {
    var psiManager = PsiManager.getInstance(project);
    var psiFiles = new ArrayList<PsiFile>(files.length);

    for (var vFile : files) {
      if (!vFile.isValid()) {
        continue;
      }
      var psiFile = psiManager.findFile(vFile);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      } else {
        SonarLintConsole.get(project).error("Couldn't find PSI for file: " + vFile.getPath());
      }
    }
    return psiFiles;
  }
}
