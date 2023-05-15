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
package org.sonarlint.intellij.actions.filters;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.actions.AbstractSonarToggleAction;
import org.sonarlint.intellij.actions.SonarLintToolWindow;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class ShowResolvedHotspotsAction extends AbstractSonarToggleAction {

  public ShowResolvedHotspotsAction() {
    super("Show Resolved Security Hotspots", "Show resolved security hotspots", SonarLintIcons.HOTSPOT_CHECKED);
  }

  @Override
  public boolean isSelected(AnActionEvent event) {
    return FilterSecurityHotspotSettings.isResolved();
  }

  @Override
  public void setSelected(AnActionEvent event, boolean flag) {
    var p = event.getProject();
    if (p != null) {
      FilterSecurityHotspotSettings.setResolved(flag);
      getService(p, SonarLintToolWindow.class).filterSecurityHotspotTab(FilterSecurityHotspotSettings.getCurrentlySelectedFilter());
    }
  }

}
