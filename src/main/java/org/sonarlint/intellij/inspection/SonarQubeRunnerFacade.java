/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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
package org.sonarlint.intellij.inspection;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import java.io.File;
import java.util.Properties;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NotNull;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonar.runner.api.IssueListener;
import org.sonar.runner.api.LogOutput;
import org.sonarlint.intellij.config.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.SonarLintProjectSettings;
import org.sonarlint.intellij.console.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintConstants;

public final class SonarQubeRunnerFacade extends AbstractProjectComponent {

  private final SonarLintProjectSettings projectSettings;
  private final SonarLintConsole console;
  private boolean started;
  private EmbeddedRunner runner;

  protected SonarQubeRunnerFacade(Project project, SonarLintProjectSettings projectSettings, SonarLintConsole console) {
    super(project);
    this.projectSettings = projectSettings;
    this.console = console;
  }

  @Override
  public void projectOpened() {
    // Nothing to do
  }

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
      runner.runAnalysis(props, issueListener);
    }
  }

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
    Properties globalProps = new Properties();
    globalProps.setProperty(SonarLintConstants.SONAR_URL, serverUrl);
    globalProps.setProperty(SonarLintConstants.ANALYSIS_MODE, SonarLintConstants.ANALYSIS_MODE_ISSUES);
    final boolean verboseEnabled = projectSettings.isVerboseEnabled();
    globalProps.setProperty(SonarLintConstants.VERBOSE_PROPERTY, Boolean.toString(verboseEnabled));

    File baseDir = new File(myProject.getBasePath());
    File projectSpecificWorkDir = new File(new File(baseDir, ProjectCoreUtil.DIRECTORY_BASED_PROJECT_DIR), "sonarlint");

    globalProps.setProperty(SonarLintConstants.WORK_DIR, projectSpecificWorkDir.getAbsolutePath());
    runner = EmbeddedRunner.create(new LogOutput() {

      @Override
      public void log(String msg, Level level) {
        switch (level) {
          case TRACE:
          case DEBUG:
            if (verboseEnabled) {
              console.info(msg);
            }
            break;
          case ERROR:
            console.error(msg);
            break;
          case INFO:
          case WARN:
          default:
            console.info(msg);
        }

      }
    })
      .setApp("IntelliJ IDEA", ApplicationInfo.getInstance().getFullVersion())
      .addGlobalProperties(globalProps);
    try {
      console.info("Starting SonarLint");
      runner.start(preferCache);
      String version = runner.serverVersion();
      this.started = version != null;
      console.info("Scanner version: " + version);
    } catch (Throwable e) {
      console.error("Unable to start SonarLint", e);
      runner = null;
      started = false;
    }
  }

  @CheckForNull
  public String getVersion() {
    return runner != null ? runner.serverVersion() : null;
  }

  public synchronized void stop() {
    if (runner != null) {
      runner.stop();
      runner = null;
    }
    started = false;
  }

  @Override
  public void projectClosed() {
    stop();
  }

  @Override
  public void initComponent() {
    // Nothing to do
  }

  @Override
  public void disposeComponent() {
    // Nothing to do
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "SonarQubeRunner";
  }

}
