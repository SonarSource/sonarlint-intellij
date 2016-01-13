/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import org.jetbrains.annotations.NotNull;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonar.runner.api.IssueListener;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintConstants;
import org.sonarlint.intellij.util.SonarLogOutput;

import javax.annotation.CheckForNull;
import java.io.File;
import java.util.Properties;

public final class DefaultSonarQubeRunnerFacade extends AbstractProjectComponent implements SonarQubeRunnerFacade {

  private final SonarLintProjectSettings projectSettings;
  private final SonarLintConsole console;
  private boolean started;
  private EmbeddedRunner runner;

  public DefaultSonarQubeRunnerFacade(Project project, SonarLintProjectSettings projectSettings, SonarLintConsole console) {
    super(project);
    this.projectSettings = projectSettings;
    this.console = console;
  }

  @Override
  public void projectClosed() {
    stop();
  }

  @Override
  public synchronized void startAnalysis(Properties props, IssueListener issueListener) {
    if (!started) {
      SonarLintGlobalSettings settings = SonarLintGlobalSettings.getInstance();
      tryStart(settings.getServerUrl(), true);
    }
    if (started) {
      if (projectSettings.isVerboseEnabled()) {
        props.setProperty(SonarLintConstants.VERBOSE_PROPERTY, "true");
      }
      props.putAll(projectSettings.getAdditionalProperties());
      if (projectSettings.isVerboseEnabled()) {
        SonarApplication sonarLint = ApplicationManager.getApplication().getComponent(SonarApplication.class);
        console.info(String.format("SonarLint [%s] properties:%n%s", sonarLint.getVersion(), propsToString(props)));
      }

      runner.runAnalysis(props, issueListener);
    }
  }

  private static String propsToString(Properties props) {
    StringBuilder builder = new StringBuilder();
    for (Object key : props.keySet()) {
      builder.append(key).append("=").append(props.getProperty(key.toString())).append("\n");
    }
    return builder.toString();
  }

  @Override
  public synchronized void tryUpdate() {
    stop();
    SonarLintGlobalSettings settings = SonarLintGlobalSettings.getInstance();
    tryStart(settings.getServerUrl(), false);
    if (!started) {
      return;
    }

    runner.syncProject(null);
  }

  private void tryStart(String serverUrl, boolean preferCache) {
    SonarApplication sonarLint = ApplicationManager.getApplication().getComponent(SonarApplication.class);
    Properties globalProps = new Properties();
    globalProps.setProperty(SonarLintConstants.SONAR_URL, serverUrl);
    globalProps.setProperty(SonarLintConstants.ANALYSIS_MODE, SonarLintConstants.ANALYSIS_MODE_ISSUES);
    globalProps.setProperty(SonarLintConstants.VERBOSE_PROPERTY, Boolean.toString(projectSettings.isVerboseEnabled()));
    globalProps.setProperty(SonarLintConstants.USE_WS_CACHE, Boolean.toString(true));

    File baseDir = new File(myProject.getBasePath());
    File projectSpecificWorkDir = new File(new File(baseDir, ProjectCoreUtil.DIRECTORY_BASED_PROJECT_DIR), "sonarlint");

    globalProps.setProperty(SonarLintConstants.WORK_DIR, projectSpecificWorkDir.getAbsolutePath());
    runner = EmbeddedRunner.create(new SonarLogOutput(console))
      .setApp("IntelliJ IDEA", ApplicationInfo.getInstance().getFullVersion())
      .addGlobalProperties(globalProps);

    try {
      console.info("Starting SonarLint " + sonarLint.getVersion());
      runner.start(preferCache);
      String version = runner.serverVersion();
      this.started = version != null;
      console.info("Scanner version: " + version);
    } catch (Exception e) {
      console.error("Unable to start SonarLint", e);
      runner = null;
      started = false;
    }
  }

  @CheckForNull
  @Override
  public String getVersion() {
    return runner != null ? runner.serverVersion() : null;
  }

  @Override
  public synchronized void stop() {
    if (runner != null) {
      runner.stop();
      runner = null;
    }
    started = false;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "SonarQubeRunner";
  }

}
