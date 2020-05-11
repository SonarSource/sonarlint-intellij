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
import java.util.Optional;
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
  private Set<String> includedRules;
  @Deprecated
  private Set<String> excludedRules;
  private Map<String, Rule> rules = new HashMap<>();

  public static SonarLintGlobalSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(SonarLintGlobalSettings.class);
  }

  public void setRuleParam(String ruleKey, String paramName, String paramValue) {
    rules.computeIfAbsent(ruleKey, s -> new Rule(true));
    rules.get(ruleKey).params.put(paramName, paramValue);
  }

  public Optional<String> getRuleParamValue(String ruleKey, String paramName) {
    if (!rules.containsKey(ruleKey) || !rules.get(ruleKey).params.containsKey(paramName)) {
      return Optional.empty();
    }
    return Optional.of(rules.get(ruleKey).params.get(paramName));
  }

  public void enableRule(String ruleKey) {
    setRuleActive(ruleKey, true);
  }

  public void disableRule(String ruleKey) {
    setRuleActive(ruleKey, false);
  }

  private void setRuleActive(String ruleKey, boolean active) {
    rules.computeIfAbsent(ruleKey, s -> new Rule(active)).isActive = active;
  }

  public boolean isRuleExplicitlyDisabled(String ruleKey) {
    if (!rules.containsKey(ruleKey)) {
      return false;
    }
    return !rules.get(ruleKey).isActive;
  }

  public void resetRuleParam(String ruleKey, String paramName) {
    if (rules.containsKey(ruleKey)) {
      rules.get(ruleKey).params.remove(paramName);
    }
  }

  @Override
  public SonarLintGlobalSettings getState() {
    return this;
  }

  @Override
  public void loadState(SonarLintGlobalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
    if(includedRules != null) {
      includedRules.forEach(it -> {
        rules.put(it, new Rule(true));
      });
    }
    if(excludedRules != null) {
      excludedRules.forEach(it -> {
        rules.put(it, new Rule(false));
      });
    }
    includedRules = null;
    excludedRules = null;
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
    if (includedRules != null) {
      return includedRules;
    } else {
      return rules.entrySet().stream()
        .filter(it -> it.getValue().isActive)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
    }

  }

  public Map<String, Rule> getRules() {
    return rules;
  }

  public void setRules(Map<String, Rule> rules) {
    this.rules = rules;
  }

  public void setIncludedRules(Set<String> includedRules) {
    this.includedRules = new HashSet<>(includedRules);
  }

  public Set<String> getExcludedRules() {
    if (rules.isEmpty() && excludedRules != null && !excludedRules.isEmpty()) {
      return excludedRules;
    } else {
      return rules.entrySet().stream()
        .filter(it -> !it.getValue().isActive)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
    }
  }

  public void setExcludedRules(Set<String> excludedRules) {
    this.excludedRules = new HashSet<>(excludedRules);
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

  public static class Rule {
    boolean isActive;
    Map<String, String> params = new HashMap<>();

    Rule() {
      this(false);
    }

    public Rule(boolean isActive) {
      this.isActive = isActive;
    }

    public boolean isActive() {
      return isActive;
    }

    public void setActive(boolean active) {
      isActive = active;
    }

    public Map<String, String> getParams() {
      return params;
    }

    public void setParams(Map<String, String> params) {
      this.params = params;
    }
  }

}
