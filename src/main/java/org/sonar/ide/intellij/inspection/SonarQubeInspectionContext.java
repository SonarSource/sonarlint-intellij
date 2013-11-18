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
package org.sonar.ide.intellij.inspection;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.config.SonarQubeSettings;
import org.sonar.ide.intellij.model.ISonarIssue;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.wsclient.ISonarWSClientFacade;
import org.sonar.ide.intellij.wsclient.WSClientFactory;

import java.io.File;
import java.util.List;

public class SonarQubeInspectionContext implements GlobalInspectionContextExtension<SonarQubeInspectionContext> {

  private static final Logger LOG = Logger.getInstance(SonarQubeInspectionContext.class);

  private List<ISonarIssue> remoteIssues;
  private File jsonReport;
  private SonarQubeServer server;
  private boolean debugEnabled;

  public static final Key<SonarQubeInspectionContext> KEY = Key.create("SonarQubeInspectionContext");

  @Override
  public Key<SonarQubeInspectionContext> getID() {
    return KEY;
  }

  public List<ISonarIssue> getRemoteIssues() {
    return remoteIssues;
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public SonarQubeServer getServer() {
    return server;
  }

  public File getJsonReport() {
    return jsonReport;
  }

  @Override
  public void performPreRunActivities(List<Tools> globalTools, List<Tools> localTools, GlobalInspectionContext context) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    Project p = context.getProject();
    if (p != null) {
      ProjectSettings projectSettings = p.getComponent(ProjectSettings.class);
      String serverId = projectSettings.getServerId();
      if (serverId == null) {
        System.out.println("Project is not associated to SonarQube");
        return;
      }
      SonarQubeSettings settings = SonarQubeSettings.getInstance();
      server = settings.getServer(serverId);
      if (server == null) {
        System.out.println("Project was associated to a server that is not configured: " + serverId);
        return;
      }
      ISonarWSClientFacade sonarClient = WSClientFactory.getInstance().getSonarClient(server);
      remoteIssues = sonarClient.getRemoteIssuesRecursively(projectSettings.getProjectKey());

      debugEnabled = LOG.isDebugEnabled();
      String jvmArgs = "";

      MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(p);
      ModuleManager moduleManager = ModuleManager.getInstance(p);
      Module[] ijModules = moduleManager.getModules();
      if (ijModules.length == 1) {
        jsonReport = new SonarRunnerAnalysis().analyzeSingleModuleProject(indicator, p, projectSettings, server, debugEnabled, jvmArgs);
      } else {
        // Use Maven
        jsonReport = new MavenAnalysis().runMavenAnalysis(p, projectSettings, server, debugEnabled);
      }
    }
  }

  @Override
  public void performPostRunActivities(List<InspectionProfileEntry> inspections, GlobalInspectionContext context) {
  }

  @Override
  public void cleanup() {
  }

}
