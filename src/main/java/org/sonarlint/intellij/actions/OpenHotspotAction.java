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
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.core.SecurityHotspotMatcher;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.issue.hotspot.SecurityHotspotOpener;
import org.sonarlint.intellij.ui.OpenHotspotDialog;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class OpenHotspotAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    OpenHotspotDialog openHotspotDialog = new OpenHotspotDialog();
    if (openHotspotDialog.showAndGet()) {
      openHotspot(project, openHotspotDialog.getHotspotId());
    }
  }

  private static void openHotspot(Project project, String hotspotId) {
    try {
      new SecurityHotspotOpener(getConnectedServerConfig(project), new SecurityHotspotMatcher(project))
        .open(project, hotspotId, getSettingsFor(project).getProjectKey());
    } catch (InvalidBindingException invalidBindingException) {
      Messages.showErrorDialog("The project binding is invalid", "Error Fetching Security Hotspot");
    }
  }

  private static ServerConfiguration getConnectedServerConfig(Project project) throws InvalidBindingException {
    ProjectBindingManager bindingManager = SonarLintUtils.getService(project, ProjectBindingManager.class);
    SonarQubeServer server = bindingManager.getSonarQubeServer();
    return SonarLintUtils.getServerConfiguration(server);
  }
}
