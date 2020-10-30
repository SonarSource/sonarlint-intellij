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
package org.sonarlint.intellij.config.project;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SonarLintProjectSettings {

  private boolean verboseEnabled = false;
  private boolean analysisLogsEnabled = false;
  private Map<String, String> additionalProperties = new LinkedHashMap<>();
  private boolean bindingEnabled = false;
  private String serverId = null;
  private String projectKey = null;
  private List<String> fileExclusions = new ArrayList<>();

  public boolean isVerboseEnabled() {
    return verboseEnabled;
  }

  public void setVerboseEnabled(boolean verboseEnabled) {
    this.verboseEnabled = verboseEnabled;
  }

  @CheckForNull
  public String getProjectKey() {
    return projectKey;
  }

  public void setProjectKey(@Nullable String projectKey) {
    this.projectKey = projectKey;
  }

  public Map<String, String> getAdditionalProperties() {
    return additionalProperties;
  }

  public void setAdditionalProperties(Map<String, String> additionalProperties) {
    this.additionalProperties = new LinkedHashMap<>(additionalProperties);
  }

  @CheckForNull
  public String getServerId() {
    return serverId;
  }

  public void setServerId(@Nullable String serverId) {
    this.serverId = serverId;
  }

  public boolean isBindingEnabled() {
    return bindingEnabled;
  }

  public void setBindingEnabled(boolean bindingEnabled) {
    this.bindingEnabled = bindingEnabled;
  }

  public boolean isAnalysisLogsEnabled() {
    return analysisLogsEnabled;
  }

  public void setAnalysisLogsEnabled(boolean analysisLogsEnabled) {
    this.analysisLogsEnabled = analysisLogsEnabled;
  }

  public List<String> getFileExclusions() {
    return fileExclusions;
  }

  public void setFileExclusions(List<String> fileExclusions) {
    this.fileExclusions = new ArrayList<>(fileExclusions);
  }

  public boolean isBoundWith(String projectKey) {
    return isBindingEnabled() && this.projectKey.equals(projectKey);
  }
}
