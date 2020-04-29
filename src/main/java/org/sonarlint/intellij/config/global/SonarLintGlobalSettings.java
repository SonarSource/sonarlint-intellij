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
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
  @Deprecated
  private final Set<String> includedRules = new HashSet<>();
  @Deprecated
  private final Set<String> excludedRules = new HashSet<>();
  private final Map<String, Rule> rules = new HashMap<>();

  public static SonarLintGlobalSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(SonarLintGlobalSettings.class);
  }

  public void setRuleParam(String ruleKey, String paramName, String paramValue) {
    if (!rules.containsKey(ruleKey)) {
      rules.put(ruleKey, new Rule(true));
    }
    rules.get(ruleKey).params.put(paramName, paramValue);
  }

  public String getRuleParam(String ruleKey, String paramName) {
    if (!rules.containsKey(ruleKey)) {
      return null;
    }
    return rules.get(ruleKey).params.get(paramName);
  }

  public void enableRule(String ruleKey) {
    setRuleActive(ruleKey, true);
  }

  public void disableRule(String ruleKey) {
    setRuleActive(ruleKey, false);
  }

  private void setRuleActive(String ruleKey, boolean active) {
    if (!rules.containsKey(ruleKey)) {
      rules.put(ruleKey, new Rule(false));
    }
    rules.get(ruleKey).isActive = active;
  }

  public boolean isRuleActive(String ruleKey) {
    if (!rules.containsKey(ruleKey)) {
      return true;
    }
    return rules.get(ruleKey).isActive;
  }

  @Override
  public SonarLintGlobalSettings getState() {
    return this;
  }

  @Override
  public void loadState(SonarLintGlobalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
    includedRules.forEach(it -> {
      rules.put(it, new Rule(true));
    });
    excludedRules.forEach(it -> {
      rules.put(it, new Rule(false));
    });
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
    return rules.entrySet().stream()
      .filter(it -> it.getValue().isActive)
      .map(Map.Entry::getKey)
      .collect(Collectors.toSet());
  }

  public Set<String> getExcludedRules() {
    return rules.entrySet().stream()
      .filter(it -> !it.getValue().isActive)
      .map(Map.Entry::getKey)
      .collect(Collectors.toSet());
  }

  public boolean isAutoTrigger() {
    return autoTrigger;
  }

  public void setAutoTrigger(boolean autoTrigger) {
    this.autoTrigger = autoTrigger;
  }

  public List<SonarQubeServer> getSonarQubeServers() {
    return this.servers;
  }

  public void setSonarQubeServers(List<SonarQubeServer> servers) {
    this.servers = Collections.unmodifiableList(servers.stream()
      .filter(s -> !SonarLintUtils.isBlank(s.getName()))
      .collect(Collectors.toList()));
  }

  public List<String> getFileExclusions() {
    return fileExclusions;
  }

  public void setFileExclusions(List<String> fileExclusions) {
    this.fileExclusions = Collections.unmodifiableList(new ArrayList<>(fileExclusions));
  }

  static class Rule {
    boolean isActive;
    Map<String, String> params = new HashMap<>();

    public Rule(boolean isActive) {
      this.isActive = isActive;
    }

  }

}
