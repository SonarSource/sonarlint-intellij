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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collections;

import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarAnalyzeEditorFileAction extends AbstractSonarAction {
  @Override
  protected boolean isEnabled(Project project, SonarLintStatus status) {
    if (status.isRunning()) {
      return false;
    }

    return SonarLintUtils.get(SonarLintAppUtils.class).getSelectedFile(project) != null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project p = e.getProject();

    if (p == null) {
      return;
    }

    SonarLintAppUtils utils = SonarLintUtils.get(SonarLintAppUtils.class);
    VirtualFile selectedFile = utils.getSelectedFile(p);
    SonarLintConsole console = SonarLintConsole.get(p);

    if (selectedFile == null) {
      console.error("No files for analysis");
      return;
    }

    Module m = utils.findModuleForFile(selectedFile, p);

    if (utils.shouldAnalyze(selectedFile, m)) {
      SonarLintJobManager jobManager = SonarLintUtils.get(p, SonarLintJobManager.class);
      if (executeBackground(e)) {
        jobManager.submitAsync(m, Collections.singleton(selectedFile), TriggerType.ACTION);
      } else {
        jobManager.submit(m, Collections.singleton(selectedFile), TriggerType.ACTION);
      }
    } else {
      console.error("File '" + selectedFile + "' cannot be analyzed");
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
