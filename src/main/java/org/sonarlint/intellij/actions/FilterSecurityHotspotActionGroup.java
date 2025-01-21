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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.actions.filters.FilterSecurityHotspotAction;
import org.sonarlint.intellij.actions.filters.FilterSecurityHotspotSettings;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;

public class FilterSecurityHotspotActionGroup extends ActionGroup {

  private final FilterSecurityHotspotAction[] myChildren;
  private final FilterSecurityHotspotSettings settings;

  public FilterSecurityHotspotActionGroup(String title, String description, @Nullable Icon icon) {
    super(title, description, icon);
    setPopup(true);

    settings = new FilterSecurityHotspotSettings();

    myChildren = new FilterSecurityHotspotAction[] {
      new FilterSecurityHotspotAction(SecurityHotspotFilters.SHOW_ALL, settings),
      new FilterSecurityHotspotAction(SecurityHotspotFilters.LOCAL_ONLY, settings),
      new FilterSecurityHotspotAction(SecurityHotspotFilters.EXISTING_ON_SERVER, settings)
    };
  }

  public void resetToDefaultSettings(AnActionEvent e) {
    for (var child : myChildren) {
      if (child.getFilter() == SecurityHotspotFilters.DEFAULT_FILTER) {
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
