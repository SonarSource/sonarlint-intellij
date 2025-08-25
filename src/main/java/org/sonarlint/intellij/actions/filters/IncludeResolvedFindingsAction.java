/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.actions.filters;

import com.intellij.openapi.actionSystem.AnActionEvent;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.sonarlint.intellij.actions.AbstractSonarToggleAction;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.finding.Finding;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class IncludeResolvedFindingsAction<T extends Finding> extends AbstractSonarToggleAction {

  private final Class<T> type;
  private boolean isResolved;

  public IncludeResolvedFindingsAction(@Nullable String text, @Nullable String description, @Nullable Icon icon, Class<T> type) {
    super(text, description, icon);
    this.type = type;
    isResolved = false;
  }

  @Override
  public boolean isSelected(AnActionEvent event) {
    return isResolved;
  }

  @Override
  public void setSelected(AnActionEvent event, boolean flag) {
    var p = event.getProject();
    if (p != null && type == Finding.class) {
        isResolved = flag;
        getService(p, SonarLintToolWindow.class).filterCurrentFileTab(isResolved);
      }
  }

}
