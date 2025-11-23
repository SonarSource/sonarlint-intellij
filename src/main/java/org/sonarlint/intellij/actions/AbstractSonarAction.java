/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.AnalysisStatus;

public abstract class AbstractSonarAction extends AnAction implements DumbAware {
  protected AbstractSonarAction() {
    super();
  }

  protected AbstractSonarAction(@Nullable String text) {
    super(text);
  }

  protected AbstractSonarAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void update(AnActionEvent e) {
    var p = e.getProject();
    if (p == null || !p.isInitialized() || p.isDisposed()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    var visible = isVisible(e);
    e.getPresentation().setVisible(visible);
    if (!visible) {
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabled(isEnabled(e, p, AnalysisStatus.get(p)));
    updatePresentation(e, p);
  }

  static boolean isRiderSlnOrCsproj(VirtualFile[] files) {
    return Stream.of(files)
      .allMatch(f -> f.getName().endsWith(".sln") || f.getName().endsWith(".slnx") || f.getName().endsWith(".csproj"));
  }

  /**
   * Whether the action should be visible in a place.
   * Examples: MainMenu, ProjectViewPopup, GoToAction
   *
   * @see com.intellij.openapi.actionSystem.ActionPlaces
   */
  protected boolean isVisible(AnActionEvent e) {
    return true;
  }

  protected boolean isEnabled(AnActionEvent e, Project project, AnalysisStatus status) {
    return true;
  }

  protected void updatePresentation(AnActionEvent e, Project project) {
    // let subclasses change text and icon
  }
}
