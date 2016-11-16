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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
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
    Project project = e.getProject();

    if (project == null) {
      return;
    }

    SonarLintSubmitter submitter = SonarLintUtils.get(project, SonarLintSubmitter.class);
    VirtualFile selectedFile = SonarLintUtils.get(SonarLintAppUtils.class).getSelectedFile(project);

    if (selectedFile == null) {
      SonarLintUtils.get(project, SonarLintConsole.class).error("No files for analysis");
      return;
    }

    submitter.submitFiles(new VirtualFile[] {selectedFile}, TriggerType.ACTION, false, executeBackground(e));
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
