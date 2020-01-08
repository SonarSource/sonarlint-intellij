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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.util.SonarLintBundle;
import org.sonarlint.intellij.util.SonarLintUtils;

@State(name = "SonarLintGlobalSettings", storages = {@Storage("sonarlint.xml")})
public final class SonarLintGlobalSettings extends ApplicationComponent.Adapter implements PersistentStateComponent<SonarLintGlobalSettings>, ExportableApplicationComponent {

  private boolean autoTrigger = true;
  private List<SonarQubeServer> servers = new LinkedList<>();
  private List<String> fileExclusions = new LinkedList<>();
  private Set<String> includedRules = new HashSet<>();
  private Set<String> excludedRules = new HashSet<>();

  public static SonarLintGlobalSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(SonarLintGlobalSettings.class);
  }

  @Override
  public SonarLintGlobalSettings getState() {
    return this;
  }

  @Override
  public void loadState(SonarLintGlobalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[] {PathManager.getOptionsFile("sonarlint")};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return SonarLintBundle.message("sonarlint.settings");
  }

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "SonarLintGlobalSettings";
  }

  public Set<String> getIncludedRules() {
    return includedRules;
  }

  public Set<String> getExcludedRules() {
    return excludedRules;
  }

  public void setIncludedRules(Set<String> includedRules) {
    this.includedRules = includedRules;
  }

  public void setExcludedRules(Set<String> excludedRules) {
    this.excludedRules = excludedRules;
  }

  public boolean isAutoTrigger() {
    return autoTrigger;
  }

  public void setAutoTrigger(boolean autoTrigger) {
    this.autoTrigger = autoTrigger;
  }

  public void setSonarQubeServers(List<SonarQubeServer> servers) {
    this.servers = Collections.unmodifiableList(servers.stream()
      .filter(s -> !SonarLintUtils.isBlank(s.getName()))
      .collect(Collectors.toList()));
  }

  public List<SonarQubeServer> getSonarQubeServers() {
    return this.servers;
  }

  public List<String> getFileExclusions() {
    return fileExclusions;
  }

  public void setFileExclusions(List<String> fileExclusions) {
    this.fileExclusions = Collections.unmodifiableList(new ArrayList<>(fileExclusions));
  }
}
