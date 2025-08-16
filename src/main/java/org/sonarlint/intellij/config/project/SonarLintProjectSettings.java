/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.util.xmlb.annotations.OptionTag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.ServerConnection;

public final class SonarLintProjectSettings {

  private final Map<String, String> moduleMapping = new LinkedHashMap<>();
  private boolean verboseEnabled = false;
  private Map<String, String> additionalProperties = new LinkedHashMap<>();
  private boolean bindingEnabled = false;
  // For backward compatibility
  @OptionTag("serverId")
  private String connectionName = null;
  private String projectKey = null;
  private List<String> fileExclusions = new ArrayList<>();
  private boolean bindingSuggestionsEnabled = true;

  public boolean isVerboseEnabled() {
    return verboseEnabled || "true".equals(System.getProperty("sonarlint.logs.verbose"));
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
  public String getConnectionName() {
    return connectionName;
  }

  public void setConnectionName(@Nullable String connectionName) {
    this.connectionName = connectionName;
  }

  public boolean isBindingEnabled() {
    return bindingEnabled;
  }

  public void setBindingEnabled(boolean bindingEnabled) {
    this.bindingEnabled = bindingEnabled;
  }

  public List<String> getFileExclusions() {
    return fileExclusions;
  }

  public void setFileExclusions(List<String> fileExclusions) {
    this.fileExclusions = new ArrayList<>(fileExclusions);
  }

  public boolean isBound() {
    return isBindingEnabled() && this.projectKey != null && connectionName != null;
  }

  public boolean isBoundTo(ServerConnection connection) {
    return isBindingEnabled() && connection.getName().equals(connectionName);
  }

  public void bindTo(@NotNull ServerConnection connection, @NotNull String projectKey) {
    bindingEnabled = true;
    connectionName = connection.getName();
    this.projectKey = projectKey;
  }

  public void unbind() {
    bindingEnabled = false;
    connectionName = null;
    this.projectKey = null;
  }

  public void setBindingSuggestionsEnabled(boolean enabled) {
    bindingSuggestionsEnabled = enabled;
  }

  public boolean isBindingSuggestionsEnabled() {
    return bindingSuggestionsEnabled;
  }

  public Map<String, String> getModuleMapping() {
    return moduleMapping;
  }

}
