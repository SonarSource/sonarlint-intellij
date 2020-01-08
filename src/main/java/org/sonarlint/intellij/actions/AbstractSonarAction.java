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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import javax.swing.Icon;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.SonarLintStatus;

public abstract class AbstractSonarAction extends AnAction {
  public AbstractSonarAction() {
    super();
  }

  public AbstractSonarAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(AnActionEvent e) {
    Project p = e.getProject();

    if (isVisible(e.getPlace())) {
      e.getPresentation().setVisible(true);
    } else {
      e.getPresentation().setVisible(false);
      e.getPresentation().setEnabled(false);
      return;
    }

    if (p == null || !p.isInitialized() || p.isDisposed()) {
      e.getPresentation().setEnabled(false);
    } else {
      SonarLintStatus status = SonarLintStatus.get(p);
      e.getPresentation().setEnabled(isEnabled(e, p, status));
    }
  }

  /**
   * Whether the action should be visible in a place.
   * Examples: MainMenu, ProjectViewPopup, GoToAction
   *
   * @see com.intellij.openapi.actionSystem.ActionPlaces
   */
  protected boolean isVisible(String place) {
    return true;
  }

  protected abstract boolean isEnabled(AnActionEvent e, Project project, SonarLintStatus status);
}
