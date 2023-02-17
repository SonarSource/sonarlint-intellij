/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.openapi.actionSystem.ToggleAction;
import java.util.ArrayList;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilterSettings;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class SecurityHotspotFilterAction extends ActionGroup {

  private final AnAction[] myChildren;

  public SecurityHotspotFilterAction(String title, String description, @Nullable Icon icon) {
    super(title, description, icon);
    setPopup(true);

    final ArrayList<AnAction> kids = new ArrayList<>(3);
    var settings = new SecurityHotspotFilterSettings();
    kids.add(new SetSecurityHotspotFilterAction(SecurityHotspotFilters.SHOW_ALL, settings));
    kids.add(new SetSecurityHotspotFilterAction(SecurityHotspotFilters.LOCAL_ONLY, settings));
    kids.add(new SetSecurityHotspotFilterAction(SecurityHotspotFilters.EXISTING_ON_SONARQUBE, settings));
    myChildren = kids.toArray(AnAction.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myChildren;
  }

  private static class SetSecurityHotspotFilterAction extends ToggleAction {

    private final SecurityHotspotFilters filter;
    private final SecurityHotspotFilterSettings settings;

    SetSecurityHotspotFilterAction(SecurityHotspotFilters filter, SecurityHotspotFilterSettings settings)  {
      super(filter.getTitle());
      this.filter = filter;
      this.settings = settings;
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

}
