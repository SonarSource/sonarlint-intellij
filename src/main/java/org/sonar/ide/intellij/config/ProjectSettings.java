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
package org.sonar.ide.intellij.config;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.util.SonarQubeConstants;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonar.runner.api.IssueListener;
import org.sonar.runner.api.LogOutput;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@State(
    name = "SonarQubeConfiguration",
    storages = {
        @Storage(id = "default", file = StoragePathMacros.PROJECT_FILE),
        @Storage(id = "dir", file = StoragePathMacros.PROJECT_CONFIG_DIR + "/sonarqube.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public final class ProjectSettings implements PersistentStateComponent<ProjectSettings>, ProjectComponent {

  private String serverId = null;
  private String projectKey = null;
  private boolean verboseEnabled = false;

  @CheckForNull
  public String getServerId() {
    return serverId;
  }

  public void setServerId(@Nullable String serverId) {
    this.serverId = serverId;
  }

  @CheckForNull
  public String getProjectKey() {
    return projectKey;
  }

  public void setProjectKey(@Nullable String projectKey) {
    this.projectKey = projectKey;
  }

  public void unassociate() {
    this.serverId = null;
    this.projectKey = null;
  }

  public boolean isAssociated() {
    return this.serverId != null && this.projectKey != null;
  }

  public ProjectSettings getState() {
    return this;
  }

  public void loadState(ProjectSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
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
    return "ProjectSettings";
  }

  public boolean isVerboseEnabled() {
    return verboseEnabled;
  }

  public void setVerboseEnabled(boolean verboseEnabled) {
    this.verboseEnabled = verboseEnabled;
  }
}
