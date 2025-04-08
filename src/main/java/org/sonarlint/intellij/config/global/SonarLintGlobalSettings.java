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
package org.sonarlint.intellij.config.global;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XMap;
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
import org.sonarlint.intellij.common.util.SonarLintUtils;

import static org.sonarlint.intellij.common.util.SonarLintUtils.equalsIgnoringTrailingSlash;

public final class SonarLintGlobalSettings {
  private boolean isFocusOnNewCode = false;
  private boolean isPromotionDisabled = false;

  private boolean autoTrigger = true;
  private boolean isRegionEnabled = false;
  private String nodejsPath = "";

  private List<ServerConnection> servers = new LinkedList<>();
  private List<String> fileExclusions = new LinkedList<>();
  private boolean hasWalkthroughRunOnce = false;

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

  public boolean isPromotionDisabled() {
    return isPromotionDisabled;
  }

  public void setPromotionDisabled(boolean promotionDisabled) {
    isPromotionDisabled = promotionDisabled;
  }

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

  public boolean isRegionEnabled() {
    return isRegionEnabled;
  }

  public void setRegionEnabled(boolean regionEnabled) {
    this.isRegionEnabled = regionEnabled;
  }

  public String getNodejsPath() {
    return nodejsPath;
  }

  public void setNodejsPath(String nodejsPath) {
    this.nodejsPath = nodejsPath;
  }

  public boolean hasWalkthroughRunOnce() {
    return hasWalkthroughRunOnce;
  }

  public void setHasWalkthroughRunOnce(boolean hasWalkthroughRunOnce) {
    this.hasWalkthroughRunOnce = hasWalkthroughRunOnce;
  }

  // Don't change annotation, used for backward compatibility
  @OptionTag("sonarQubeServers")
  public List<ServerConnection> getServerConnections() {
    return this.servers;
  }

  public void setServerConnections(List<ServerConnection> servers) {
    this.servers = servers.stream()
      .filter(s -> !SonarLintUtils.isBlank(s.getName()))
      .toList();
  }

  public Optional<ServerConnection> getServerConnectionByName(String name) {
    return servers.stream()
      .filter(s -> name.equals(s.getName()))
      .findFirst();
  }

  public boolean connectionExists(String connectionName) {
    return getServerConnectionByName(connectionName).isPresent();
  }

  public void addServerConnection(ServerConnection connection) {
    ArrayList<ServerConnection> sonarQubeServers = new ArrayList<>(servers);
    sonarQubeServers.add(connection);
    this.servers = Collections.unmodifiableList(sonarQubeServers);
  }

  public Set<String> getServerNames() {
    return servers.stream()
      .map(ServerConnection::getName)
      .collect(Collectors.toSet());
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

  public List<ServerConnection> getConnectionsTo(String serverUrl) {
    return servers.stream()
      .filter(it -> equalsIgnoringTrailingSlash(it.getHostUrl(), serverUrl))
      .toList();
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
