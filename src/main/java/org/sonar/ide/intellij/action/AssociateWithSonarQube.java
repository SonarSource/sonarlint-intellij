/*
 * SonarQube IntelliJ
 * Copyright (C) 2013-2014 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.associate.AssociateDialog;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.util.SonarQubeBundle;
import org.sonar.ide.intellij.wsclient.ISonarRemoteProject;

public class AssociateWithSonarQube extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    Project p = e.getProject();
    if (p != null) {
      ProjectSettings settings = p.getComponent(ProjectSettings.class);
      AssociateDialog dialog = new AssociateDialog(p, settings.isAssociated());
      if (settings.isAssociated()) {
        dialog.setSelectedSonarQubeProject(settings.getServerId(), settings.getProjectKey());
      }
      dialog.show();
      processResult(p, settings, dialog);
    }
  }

  private void processResult(Project p, ProjectSettings settings, AssociateDialog dialog) {
    if (dialog.getExitCode() == AssociateDialog.UNASSOCIATE_EXIT_CODE) {
      settings.unassociate();
    } else if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      settings.setServerId(null);
      settings.setProjectKey(null);
      ISonarRemoteProject sonarProject = dialog.getSelectedSonarQubeProject();
      if (sonarProject == null) {
        settings.unassociate();
      } else {
        associate(p, settings, sonarProject);
      }
    }
  }

  private void associate(Project p, ProjectSettings settings, @NotNull ISonarRemoteProject sonarProject) {
    ModuleManager moduleManager = ModuleManager.getInstance(p);
    Module[] ijModules = moduleManager.getModules();
    SonarQubeConsole console = SonarQubeConsole.getSonarQubeConsole(p);

    settings.setServerId(sonarProject.getServer().getId());
    settings.setProjectKey(sonarProject.getKey());
  }

  @Override
  public void update(AnActionEvent e) {
    Project p = e.getProject();
    if (p != null) {
      ProjectSettings settings = p.getComponent(ProjectSettings.class);
      if (!settings.isAssociated()) {
        e.getPresentation().setText(SonarQubeBundle.message("sonarqube.associate.action.label"));
      } else {
        e.getPresentation().setText(SonarQubeBundle.message("sonarqube.associate.action.label_update"));
      }
    }
  }
}
