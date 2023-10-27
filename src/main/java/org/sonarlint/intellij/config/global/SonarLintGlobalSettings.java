/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SonarLintGlobalSettings {

  private boolean isFocusOnNewCode = false;
  private boolean autoTrigger = true;
  private String nodejsPath = "";
  private List<ServerConnectionSettings> servers = new LinkedList<>();
  private List<String> fileExclusions = new LinkedList<>();
  @Deprecated
  private Set<String> includedRules;
  @Deprecated
  private Set<String> excludedRules;
  @XCollection(propertyElementName = "rules", elementName = "rule")
  Collection<Rule> rules = new HashSet<>();
  @Transient
  Map<String, Rule> rulesByKey = new HashMap<>();
  private boolean taintVulnerabilitiesTabDisclaimerDismissed;
  private boolean secretsNeverBeenAnalysed = true;

  public void rememberNotificationOnSecretsBeenSent() {
    setSecretsNeverBeenAnalysed(false);
  }

  public boolean isSecretsNeverBeenAnalysed() {
    return secretsNeverBeenAnalysed;
  }

  public void setSecretsNeverBeenAnalysed(boolean secretsNeverBeenAnalysed) {
    this.secretsNeverBeenAnalysed = secretsNeverBeenAnalysed;
  }

  public boolean isTaintVulnerabilitiesTabDisclaimerDismissed() {
    return taintVulnerabilitiesTabDisclaimerDismissed;
  }

  public void dismissTaintVulnerabilitiesTabDisclaimer() {
    setTaintVulnerabilitiesTabDisclaimerDismissed(true);
  }

  // used for deserializing
  public void setTaintVulnerabilitiesTabDisclaimerDismissed(boolean dismissed) {
    this.taintVulnerabilitiesTabDisclaimerDismissed = dismissed;
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

  private void initializeRulesByKey() {
    this.rulesByKey = new HashMap<>(rules.stream().collect(Collectors.toMap(Rule::getKey, Function.identity())));
  }

  public Map<String, Rule> getRulesByKey() {
    migrateOldStyleRuleActivations();
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

  public void setRulesByKey(Map<String, Rule> rules) {
    this.rulesByKey = new HashMap<>(rules);
  }

  public boolean isFocusOnNewCode() {
    return isFocusOnNewCode;
  }

  public void setFocusOnNewCode(boolean focusOnNewCode) {
    isFocusOnNewCode = focusOnNewCode;
  }

  public boolean isAutoTrigger() {
    return autoTrigger;
  }

  public void setAutoTrigger(boolean autoTrigger) {
    this.autoTrigger = autoTrigger;
  }

  public String getNodejsPath() {
    return nodejsPath;
  }

  public void setNodejsPath(String nodejsPath) {
    this.nodejsPath = nodejsPath;
  }

  // Don't change annotation, used for backward compatibility
  // always use ServerConnectionService to access server connections
  @OptionTag("sonarQubeServers")
  public List<ServerConnectionSettings> getServerConnections() {
    return this.servers;
  }

  // always use ServerConnectionService to set server connections
  void setServerConnections(List<ServerConnectionSettings> servers) {
    this.servers = servers;
  }

  public List<String> getFileExclusions() {
    return fileExclusions;
  }

  public void setFileExclusions(List<String> fileExclusions) {
    this.fileExclusions = List.copyOf(fileExclusions);
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
