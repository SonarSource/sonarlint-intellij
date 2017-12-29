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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.util.Collections;
import javax.swing.Icon;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarAnalyzeFilesAction extends AbstractSonarAction {
  public SonarAnalyzeFilesAction() {
    super();
  }

  public SonarAnalyzeFilesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected boolean isEnabled(AnActionEvent e, Project project, SonarLintStatus status) {
    if (status.isRunning()) {
      return false;
    }

    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

    // TODO ideally we would already check if the files are analyzable
    return file != null;
  }

  @Override
  protected boolean isVisible(String place) {
    return true;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }

    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

    if (file == null) {
      SonarLintUtils.get(project, SonarLintConsole.class).error("No file for analysis");
      return;
    }

    SonarLintSubmitter submitter = SonarLintUtils.get(project, SonarLintSubmitter.class);
    submitter.submitFiles(Collections.singleton(file), TriggerType.ACTION, new ShowIssuesCallable(project), executeBackground(e));
  }

  private class ShowIssuesCallable implements AnalysisCallback {
    private final Project project;

    private ShowIssuesCallable(Project project) {
      this.project = project;
    }

    @Override public void onError(Throwable e) {
      // do nothing
    }

    @Override
    public void onSuccess() {
      showCurrentFileTab();
    }

    private void showCurrentFileTab() {
      UIUtil.invokeLaterIfNeeded(() -> ServiceManager.getService(project, IssuesViewTabOpener.class).openCurrentFile());
    }
  }

  /**
   * Whether the analysis should be launched in the background.
   * Analysis should be run in background in the following cases:
   * - Keybinding used (place = MainMenu)
   * - Macro used (place = unknown)
   * - Action used, ctrl+shift+A (place = GoToAction)
   */
  private static boolean executeBackground(AnActionEvent e) {
    return ActionPlaces.isMainMenuOrActionSearch(e.getPlace())
      || ActionPlaces.UNKNOWN.equals(e.getPlace());
  }
}
