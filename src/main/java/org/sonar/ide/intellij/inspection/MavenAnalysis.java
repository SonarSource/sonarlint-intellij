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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerExecutionResult;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.model.SonarQubeServer;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MavenAnalysis {

  private static final Logger LOG = Logger.getInstance(MavenAnalysis.class);

  public File runMavenAnalysis(final Project p, ProjectSettings projectSettings, SonarQubeServer server, boolean debugEnabled) {
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(p);
    if (!mavenProjectsManager.isMavenizedProject()) {
      LOG.error("This is not a Maven project");
      return null;
    }
    List<MavenProject> rootProjects = mavenProjectsManager.getRootProjects();
    if (rootProjects.size() > 1) {
      LOG.error("Maven projects with more than 1 root project are not supported");
      return null;
    }
    MavenProject rootProject = rootProjects.get(0);
    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("enforcer.skip", "true");
    File jsonReport = getJsonReportLocation(rootProject);
    GlobalConfigurator.configureAnalysis(p, jsonReport, projectSettings, server, debugEnabled, new MapParamWrapper(params));
    final MavenRunnerParameters mvnParams = new MavenRunnerParameters(true,
        rootProject.getDirectory(),
        Arrays.asList("sonar:sonar"),
        rootProject.getActivatedProfilesIds());
    final MavenRunnerSettings runnerSettings = new MavenRunnerSettings();
    runnerSettings.setMavenProperties(params);
    final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
    final ProgressIndicator progress = ProgressWrapper.wrap(original);
    MavenRunner.getInstance(p).runBatch(Arrays.asList(mvnParams), null, runnerSettings, null, progress);
    return jsonReport;
  }

  public File getJsonReportLocation(MavenProject p) {
    File projectSpecificWorkDir = new File(p.getBuildDirectory(), "sonar");
    return new File(projectSpecificWorkDir, "sonar-report.json");
  }
}
