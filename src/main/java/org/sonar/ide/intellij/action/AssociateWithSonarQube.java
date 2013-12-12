/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.sonar.ide.intellij.associate.AssociateDialog;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.wsclient.ISonarRemoteModule;
import org.sonar.ide.intellij.wsclient.ISonarRemoteProject;
import org.sonar.ide.intellij.wsclient.WSClientFactory;

import java.util.List;

public class AssociateWithSonarQube extends AnAction {

  private static final Logger LOG = Logger.getInstance(AssociateWithSonarQube.class);

  public void actionPerformed(AnActionEvent e) {
    Project p = e.getProject();
    if (p != null) {
      MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(p);
      ProjectSettings settings = p.getComponent(ProjectSettings.class);
      AssociateDialog dialog = new AssociateDialog(p, settings.isAssociated());
      if (settings.isAssociated()) {
        dialog.setSelectedSonarQubeProject(settings.getServerId(), settings.getProjectKey());
      } else {
        // Try to guess project association
        if (mavenProjectsManager.isMavenizedProject() && mavenProjectsManager.hasProjects()) {
          dialog.setFilter(mavenProjectsManager.getRootProjects().get(0).getDisplayName());
        }
      }
      dialog.show();
      processResult(p, mavenProjectsManager, settings, dialog);
    }
  }

  private void processResult(Project p, MavenProjectsManager mavenProjectsManager, ProjectSettings settings, AssociateDialog dialog) {
    if (dialog.getExitCode() == AssociateDialog.UNASSOCIATE_EXIT_CODE) {
      settings.unassociate();
    } else if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      settings.setServerId(null);
      settings.setProjectKey(null);
      ISonarRemoteProject sonarProject = dialog.getSelectedSonarQubeProject();
      if (sonarProject == null) {
        settings.unassociate();
      } else {
        associate(p, settings, mavenProjectsManager, sonarProject);
      }
    }
  }

  private void associate(Project p, ProjectSettings settings, MavenProjectsManager mavenProjectsManager, @NotNull ISonarRemoteProject sonarProject) {
    ModuleManager moduleManager = ModuleManager.getInstance(p);
    Module[] ijModules = moduleManager.getModules();
    settings.getModuleKeys().clear();
    SonarQubeConsole console = SonarQubeConsole.getSonarQubeConsole(p);
    if (ijModules.length == 1) {
      settings.getModuleKeys().put(ijModules[0].getName(), sonarProject.getKey());
    } else if (ijModules.length > 1) {
      if (!mavenProjectsManager.isMavenizedProject()) {
        console.error("Only multi-module Maven projects are supported for now");
        return;
      }
      List<MavenProject> rootProjects = mavenProjectsManager.getRootProjects();
      List<MavenProject> mavenModules = mavenProjectsManager.getProjects();
      if (rootProjects.size() > 1) {
        console.error("Maven projects with more than 1 root project are not supported");
        return;
      }
      MavenProject rootProject = rootProjects.get(0);
      String branchSuffix = guessBranchSuffix(rootProject, sonarProject.getKey());
      List<ISonarRemoteModule> sonarModules = WSClientFactory.getInstance().getSonarClient(sonarProject.getServer()).getRemoteModules(sonarProject);
      if (sonarModules.size() + 1 != mavenModules.size()) {
        console.info("Project has " + mavenModules.size() + " modules while remote SonarQube project has " + (sonarModules.size() + 1) + " modules");
      }
      for (Module ijModule : ijModules) {
        MavenProject mavenModule = mavenProjectsManager.findProject(ijModule);
        if (mavenModule == null) {
          console.error("Module " + ijModule.getName() + " is not a Maven module");
        } else {
          String expectedKey = sonarKey(mavenModule) + branchSuffix;
          if (expectedKey.equals(sonarProject.getKey())) {
            settings.getModuleKeys().put(ijModule.getName(), expectedKey);
          } else {
            boolean found = false;
            for (ISonarRemoteModule sonarModule : sonarModules) {
              if (expectedKey.equals(sonarModule.getKey())) {
                settings.getModuleKeys().put(ijModule.getName(), expectedKey);
                found = true;
                break;
              }
            }
            if (!found) {
              console.error("Unable to find matching SonarQube module for IntelliJ module " + ijModule.getName());
            }
          }
        }

      }
    }

    settings.setServerId(sonarProject.getServer().getId());
    settings.setProjectKey(sonarProject.getKey());
  }

  private String guessBranchSuffix(MavenProject rootProject, String key) {
    String rootKey = sonarKey(rootProject);
    if (key.startsWith(rootKey)) {
      return key.substring(rootKey.length());
    }
    return "";
  }

  private String sonarKey(MavenProject project) {
    return project.getMavenId().getGroupId() + ":" + project.getMavenId().getArtifactId();
  }

  @Override
  public void update(AnActionEvent e) {
    Project p = e.getProject();
    if (p != null) {
      ProjectSettings settings = p.getComponent(ProjectSettings.class);
      if (!settings.isAssociated()) {
        e.getPresentation().setText("Associate with SonarQube...");
      } else {
        e.getPresentation().setText("Update SonarQube association...");
      }
    }
  }
}
