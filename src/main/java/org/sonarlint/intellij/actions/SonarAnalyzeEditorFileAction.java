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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.analysis.SonarLintAnalyzer;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

import java.util.Collections;

public class SonarAnalyzeEditorFileAction extends AbstractSonarAction {
  @Override
  protected boolean isEnabled(SonarLintStatus status) {
    return !status.isRunning();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project p = e.getProject();
    VirtualFile[] selectedFiles = FileEditorManager.getInstance(p).getSelectedFiles();
    SonarLintConsole console = SonarLintConsole.get(p);

    if (selectedFiles.length == 0) {
      console.error("No files for analysis");
      return;
    }

    Module m = ModuleUtil.findModuleForFile(selectedFiles[0], p);

    if (SonarLintUtils.shouldAnalyze(selectedFiles[0], m)) {
      SonarLintAnalyzer analyzer = p.getComponent(SonarLintAnalyzer.class);
      if (executeBackground(e)) {
        analyzer.submitAsync(m, Collections.singleton(selectedFiles[0]));
      } else {
        analyzer.submit(m, Collections.singleton(selectedFiles[0]));
      }
    } else {
      console.error("File " + selectedFiles[0] + " cannot be analyzed");
    }
  }

  /**
   * Whether the analysis should be lauched in the background.
   * Analysis should be run in background in the following cases:
   *  - Keybinding used (place = MainMenu)
   *  - Macro used (place = unknown)
   *  - Action used, ctrl+shift+A (place = GoToAction)
   */
  private static boolean executeBackground(AnActionEvent e) {
    return ActionPlaces.isMainMenuOrActionSearch(e.getPlace())
      || ActionPlaces.UNKNOWN.equals(e.getPlace());
  }
}
