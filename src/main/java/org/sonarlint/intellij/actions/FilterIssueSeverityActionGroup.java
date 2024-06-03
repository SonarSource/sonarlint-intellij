/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.actions.filters.FilterIssueSeverityAction;
import org.sonarlint.intellij.actions.filters.FilterIssueSeveritySettings;
import org.sonarlint.intellij.actions.filters.IssueSeverityFilters;

public class FilterIssueSeverityActionGroup extends ActionGroup {

  private final FilterIssueSeverityAction[] myChildren;
  private final FilterIssueSeveritySettings settings;

  public FilterIssueSeverityActionGroup(String title, String description, @Nullable Icon icon) {
    super(title, description, icon);
    setPopup(true);

    settings = new FilterIssueSeveritySettings();

    myChildren = new FilterIssueSeverityAction[] {
      new FilterIssueSeverityAction(IssueSeverityFilters.SHOW_ALL, settings),
      new FilterIssueSeverityAction(IssueSeverityFilters.INFO, settings),
      new FilterIssueSeverityAction(IssueSeverityFilters.MINOR, settings),
      new FilterIssueSeverityAction(IssueSeverityFilters.MAJOR, settings),
      new FilterIssueSeverityAction(IssueSeverityFilters.CRITICAL, settings),
      new FilterIssueSeverityAction(IssueSeverityFilters.BLOCKER, settings)
    };
  }

  public void resetToDefaultSettings(AnActionEvent e) {
    for (var child : myChildren) {
      if (child.getFilter() == IssueSeverityFilters.DEFAULT_FILTER) {
        child.setSelected(e, true);
      }
    }
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myChildren;
  }

}
