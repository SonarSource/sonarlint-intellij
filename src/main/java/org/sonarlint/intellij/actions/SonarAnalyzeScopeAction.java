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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.trigger.OpenFilesSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.ui.scope.AbstractScope;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarAnalyzeScopeAction extends AbstractSonarAction {
  @Override
  protected boolean isEnabled(Project project, SonarLintStatus status) {
    return !status.isRunning();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project p = e.getProject();
    if (p == null) {
      return;
    }
    SonarLintConsole console = SonarLintConsole.get(p);
    SonarLintAppUtils utils = SonarLintUtils.get(SonarLintAppUtils.class);

    AbstractScope scope = e.getData(AbstractScope.SCOPE_DATA_KEY);
    if (scope == null) {
      console.error("No scope found");
      return;
    }

    Collection<VirtualFile> files = scope.getAll();

    if (files.isEmpty()) {
      console.error("No files for analysis");
      return;
    }

    Multimap<Module, VirtualFile> filesByModule = HashMultimap.create();
    for (VirtualFile file : files) {
      Module m = utils.findModuleForFile(file, p);
      if (!utils.shouldAnalyze(file, m)) {
        console.info("File '" + file + "' cannot be analyzed");
        continue;
      }

      filesByModule.put(m, file);
    }

    if (!filesByModule.isEmpty()) {
      SonarLintJobManager analyzer = SonarLintUtils.get(p, SonarLintJobManager.class);
      for (Module m : filesByModule.keySet()) {
        if (executeBackground(e)) {
          analyzer.submitAsync(m, filesByModule.get(m), TriggerType.ACTION);
        } else {
          analyzer.submit(m, filesByModule.get(m), TriggerType.ACTION);
        }
      }
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
