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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.analysis.SonarLintStatus;

public abstract class AbstractSonarAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    Project p = e.getProject();

    e.getPresentation().setVisible(true);

    if (p == null || !p.isInitialized() || p.isDisposed()) {
      e.getPresentation().setEnabled(false);
    } else {
      SonarLintStatus status = SonarLintStatus.get(p);
      e.getPresentation().setEnabled(isEnabled(p, status));
    }
  }

  protected abstract boolean isEnabled(Project project, SonarLintStatus status);
}
