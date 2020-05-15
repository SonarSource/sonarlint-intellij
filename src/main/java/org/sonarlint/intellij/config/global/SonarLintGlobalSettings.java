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
import com.intellij.util.xmlb.annotations.XMap;
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
import org.jetbrains.annotations.Nullable;
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
  @XMap
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
    // There was an issue here but it's dead
    XmlSerializerUtil.copyBean(state, this);
    if(includedRules != null && !includedRules.isEmpty()) {
      includedRules.forEach(it -> rules.put(it, new Rule(true)));
      includedRules = null;
    }
    if(excludedRules != null && !excludedRules.isEmpty()) {
      excludedRules.forEach(it -> rules.put(it, new Rule(false)));
      includedRules = null;
    }
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

  /**
   * @deprecated Must only be called to convert pre-4.8 settings to 4.8+ format
   */
  @Deprecated
  public Set<String> getIncludedRules() {
    return includedRules;
  }

  /**
   * @deprecated Must only be called to convert pre-4.8 settings to 4.8+ format
   */
  @Deprecated
  public void setIncludedRules(@Nullable Set<String> includedRules) {
    this.includedRules = includedRules == null ? null : new HashSet<>(includedRules);
  }

  /**
   * @deprecated Must only be called to convert pre-4.8 settings to 4.8+ format
   */
  @Deprecated
  public Set<String> getExcludedRules() {
    return excludedRules;
  }

  /**
   * @deprecated Must only be called to convert pre-4.8 settings to 4.8+ format
   */
  @Deprecated
  public void setExcludedRules(@Nullable Set<String> excludedRules) {
    this.excludedRules = excludedRules == null ? null : new HashSet<>(excludedRules);
  }

  public Map<String, Rule> getRules() {
    return rules;
  }

  public void setRules(Map<String, Rule> rules) {
    this.rules = new HashMap<>(rules);
  }

  public Set<String> includedRules() {
    return rules.entrySet().stream()
      .filter(it -> it.getValue().isActive)
      .map(Map.Entry::getKey)
      .collect(Collectors.toSet());
  }

  public Set<String> excludedRules() {
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

  public static class Rule {
    boolean isActive;

    @XMap
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
