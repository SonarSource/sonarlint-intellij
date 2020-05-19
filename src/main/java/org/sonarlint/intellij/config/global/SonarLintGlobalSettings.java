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
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XMap;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
  @XCollection(propertyElementName = "rules", elementName = "rule")
  private Collection<Rule> rules = new HashSet<>();
  @Transient
  private Map<String, Rule> rulesByKey = new HashMap<>();

  public static SonarLintGlobalSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(SonarLintGlobalSettings.class);
  }

  public void setRuleParam(String ruleKey, String paramName, String paramValue) {
    rulesByKey.computeIfAbsent(ruleKey, s -> new Rule(ruleKey, true)).getParams().put(paramName, paramValue);
  }

  public Optional<String> getRuleParamValue(String ruleKey, String paramName) {
    if (!rulesByKey.containsKey(ruleKey) || !rulesByKey.get(ruleKey).getParams().containsKey(paramName)) {
      return Optional.empty();
    }
    return Optional.of(rulesByKey.get(ruleKey).getParams().get(paramName));
  }

  public void enableRule(String ruleKey) {
    setRuleActive(ruleKey, true);
  }

  public void disableRule(String ruleKey) {
    setRuleActive(ruleKey, false);
  }

  private void setRuleActive(String ruleKey, boolean active) {
    rulesByKey.computeIfAbsent(ruleKey, s -> new Rule(ruleKey, active)).isActive = active;
  }

  public boolean isRuleExplicitlyDisabled(String ruleKey) {
    return rulesByKey.containsKey(ruleKey) && !rulesByKey.get(ruleKey).isActive;
  }

  public void resetRuleParam(String ruleKey, String paramName) {
    if (rulesByKey.containsKey(ruleKey)) {
      rulesByKey.get(ruleKey).params.remove(paramName);
    }
  }

  @Override
  public SonarLintGlobalSettings getState() {
    this.rules = rulesByKey.values();
    return this;
  }

  @Override
  public void loadState(SonarLintGlobalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
    initializeRulesByKey();
  }

  private void initializeRulesByKey() {
    this.rulesByKey = new HashMap<>(rules.stream().collect(Collectors.toMap(Rule::getKey, Function.identity())));
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

  public Map<String, Rule> getRulesByKey() {
    migrateOldStyleRuleActivations();
    migrateOldStyleServers();
    return rulesByKey;
  }

  private void migrateOldStyleRuleActivations() {
    if (includedRules != null && !includedRules.isEmpty()) {
      includedRules.forEach(it -> rulesByKey.put(it, new Rule(it, true)));
    }
    includedRules = null;
    if (excludedRules != null && !excludedRules.isEmpty()) {
      excludedRules.forEach(it -> rulesByKey.put(it, new Rule(it, false)));
    }
    excludedRules = null;
  }

  public Collection<Rule> getRules() {
    return rules;
  }

  public void setRules(Collection<Rule> rules) {
    this.rules = new HashSet<>(rules);
    initializeRulesByKey();
  }

  public Set<String> excludedRules() {
    return rulesByKey.entrySet().stream()
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

  private void migrateOldStyleServers() {
    this.servers.forEach(SonarQubeServer::migrateOldStyleCredentials);
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

  /**
   * @deprecated only used for serialization
   */
  @Deprecated
  public Set<String> getIncludedRules() {
    return includedRules;
  }

  /**
   * @deprecated only used for serialization
   */
  @Deprecated
  public void setIncludedRules(Set<String> includedRules) {
    this.includedRules = includedRules;
  }

  /**
   * @deprecated only used for serialization
   */
  @Deprecated
  public Set<String> getExcludedRules() {
    return excludedRules;
  }

  /**
   * @deprecated only used for serialization
   */
  @Deprecated
  public void setExcludedRules(Set<String> excludedRules) {
    this.excludedRules = excludedRules;
  }

  public static class Rule {
    String key;
    boolean isActive;

    Map<String, String> params = new HashMap<>();

    // Default constructor for XML (de)serialization
    public Rule() {
      this("", true);
    }

    public Rule(String key, boolean isActive) {
      setKey(key);
      setActive(isActive);
    }

    @Attribute
    public boolean isActive() {
      return isActive;
    }

    public void setActive(boolean active) {
      isActive = active;
    }

    @Attribute
    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    @XMap(entryTagName = "param")
    public Map<String, String> getParams() {
      return params;
    }

    public void setParams(Map<String, String> params) {
      this.params = params;
    }
  }

}
