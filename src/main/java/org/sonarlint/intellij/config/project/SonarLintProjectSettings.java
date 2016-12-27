/*
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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

@State(
  name = "SonarLintProjectSettings",
  storages = {
    @Storage(id = "default", file = StoragePathMacros.PROJECT_FILE),
    @Storage(id = "dir", file = StoragePathMacros.PROJECT_CONFIG_DIR + "/sonarlint.xml", scheme = StorageScheme.DIRECTORY_BASED)
  })
public final class SonarLintProjectSettings extends AbstractProjectComponent implements PersistentStateComponent<SonarLintProjectSettings> {

  private boolean verboseEnabled = false;

  private boolean analysisLogsEnabled = false;
  private final Map<String, String> additionalProperties = new LinkedHashMap<>();

  private boolean bindingEnabled = false;
  private String serverId = null;
  private String projectKey = null;

  /**
   * Constructor called by the XML deserialization (no args).
   * Even though this class has the scope of a project, we can't have it injected here.
   */
  public SonarLintProjectSettings() {
    super(null);
  }

  @Override
  public SonarLintProjectSettings getState() {
    return this;
  }

  @Override
  public void loadState(SonarLintProjectSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "SonarLintProjectSettings";
  }

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
    return new LinkedHashMap<>(additionalProperties);
  }

  public void setAdditionalProperties(Map<String, String> additionalProperties) {
    this.additionalProperties.clear();
    this.additionalProperties.putAll(additionalProperties);
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

}
