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
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.actions.AbstractSonarToggleAction;
import org.sonarlint.intellij.actions.SonarLintToolWindow;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class FilterSecurityHotspotAction extends AbstractSonarToggleAction {

  private final SecurityHotspotFilters filter;
  private final FilterSecurityHotspotSettings settings;

  public FilterSecurityHotspotAction(SecurityHotspotFilters filter, FilterSecurityHotspotSettings settings) {
    this.filter = filter;
    this.settings = settings;
  }

  public SecurityHotspotFilters getFilter() {
    return filter;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    var project = e.getProject();
    if (project == null) {
      return;
    }
    e.getPresentation().setText(filter.getTitle(project));
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return settings.getCurrentlySelectedFilter() == filter;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
    var project = e.getProject();
    if (project == null) {
      return;
    }

    if (enabled && settings.getCurrentlySelectedFilter() != filter) {
      getService(project, SonarLintToolWindow.class).filterSecurityHotspotTab(filter);
      settings.setCurrentlySelectedFilter(filter);
    }
  }

}
