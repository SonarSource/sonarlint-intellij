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
package org.sonar.ide.intellij.inspection;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.config.ProjectSettings;
import org.sonar.ide.intellij.config.SonarQubeSettings;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.util.SonarQubeConstants;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonar.runner.api.IssueListener;
import org.sonar.runner.api.LogOutput;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Properties;

public final class SonarQubeRunner extends AbstractProjectComponent {

  private boolean started;
  private EmbeddedRunner runner;
  private String version;
  private final ProjectSettings projectSettings;
  private final SonarQubeConsole console;

  protected SonarQubeRunner(Project project, ProjectSettings projectSettings, SonarQubeConsole console) {
    super(project);
    this.projectSettings = projectSettings;
    this.console = console;
  }

  @Override
  public void projectOpened() {
  }

  public synchronized void startAnalysis(Properties props, IssueListener issueListener) {
    startRunner();
    if (started) {
      if (projectSettings.isVerboseEnabled()) {
        props.setProperty(SonarQubeConstants.VERBOSE_PROPERTY, "true");
      }
      runner.runAnalysis(props, issueListener);
    }
  }
  
  private void startRunner() {
    if (!started) {
      SonarQubeSettings settings = SonarQubeSettings.getInstance();
      SonarQubeServer server;
      if (projectSettings.isAssociated()) {
        server = settings.getServer(projectSettings.getServerId());
      } else {
        server = settings.getDefaultServer();
      }
      tryStart(server);
    }
  }

  private void tryStart(SonarQubeServer server) {
    Properties globalProps = new Properties();
    globalProps.setProperty(SonarQubeConstants.SONAR_URL, server.getUrl());
    try {
      server.setPassword(PasswordSafe.getInstance().getPassword(null, SonarQubeServer.class, server.getId()));
    } catch (PasswordSafeException e) {
      throw new IllegalStateException("Unable to load password for server " + server.getId());
    }
    if (server.hasCredentials()) {
      globalProps.setProperty(SonarQubeConstants.SONAR_LOGIN, server.getUsername());
      globalProps.setProperty(SonarQubeConstants.SONAR_PASSWORD, server.getPassword());
    }
    globalProps.setProperty(SonarQubeConstants.ANALYSIS_MODE, SonarQubeConstants.ANALYSIS_MODE_ISSUES);
    globalProps.setProperty(SonarQubeConstants.VERBOSE_PROPERTY, Boolean.toString(projectSettings.isVerboseEnabled()));

    File baseDir = new File(myProject.getBasePath());
    File projectSpecificWorkDir = new File(new File(baseDir, ProjectCoreUtil.DIRECTORY_BASED_PROJECT_DIR), "sonarqube");

    globalProps.setProperty(SonarQubeConstants.WORK_DIR, projectSpecificWorkDir.getAbsolutePath());
    runner = EmbeddedRunner.create(new LogOutput() {

      @Override
      public void log(String msg, Level level) {
        switch (level) {
          case TRACE:
            console.info(msg);
            break;
          case DEBUG:
            console.info(msg);
            break;
          case INFO:
            console.info(msg);
            break;
          case WARN:
            console.info(msg);
            break;
          case ERROR:
            console.error(msg);
            break;
          default:
            console.info(msg);
        }

      }
    })
        .setApp("IntelliJ IDEA", ApplicationInfo.getInstance().getFullVersion())
        .addGlobalProperties(globalProps);
    try {
      console.info("Starting SonarQube for server " + server.getId());
      runner.start();
      this.version = runner.serverVersion();
      this.started = version != null;
    } catch (Throwable e) {
      console.error("Unable to start SonarQube for server " + server.getId(), e);
      runner = null;
      started = false;
    }
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
