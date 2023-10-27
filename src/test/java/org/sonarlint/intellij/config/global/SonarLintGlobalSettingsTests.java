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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class SonarLintGlobalSettingsTests extends AbstractSonarLintLightTests {

  private static final String RULE = "rule";
  private static final String RULE1 = "rule1";
  private static final String PARAM = "param";
  private static final String VALUE = "value";

  @Test
  void testRoundTrip() {
    var settings = new SonarLintGlobalSettings();
    assertThat(settings.isAutoTrigger()).isTrue();
    assertThat(settings.getNodejsPath()).isBlank();

    var server = ServerConnectionSettings.newBuilder().setName("name").build();

    settings.setServerConnections(List.of(server));
    assertThat(settings.getServerConnections()).containsOnly(server);

    settings.setAutoTrigger(false);
    assertThat(settings.isAutoTrigger()).isFalse();

    settings.setNodejsPath("path/to/node");
    assertThat(settings.getNodejsPath()).isEqualTo("path/to/node");
  }

  @Test
  void testLoadStateOldFormat() {
    var state = new SonarLintGlobalSettings();
    var includedRules = Collections.singleton(RULE);
    var excludedRules = Collections.singleton(RULE1);
    state.setIncludedRules(includedRules);
    state.setExcludedRules(excludedRules);

    var settingsStore = new SonarLintGlobalSettingsStore();
    settingsStore.loadState(state);

    var rules = state.getRulesByKey();
    assertThat(rules).containsOnlyKeys(RULE, RULE1);
    assertThat(rules.get(RULE).isActive).isTrue();
    assertThat(rules.get(RULE1).isActive).isFalse();

  }

  @Test
  void testLoadStateNewFormat() {
    var state = new SonarLintGlobalSettings();
    var settingsStore = new SonarLintGlobalSettingsStore();
    var activeRuleWithParam = new SonarLintGlobalSettings.Rule(RULE, true);
    activeRuleWithParam.setParams(Map.of("paramKey", "paramValue"));
    var inactiveRule = new SonarLintGlobalSettings.Rule(RULE1, false);
    state.setRules(List.of(activeRuleWithParam, inactiveRule));

    settingsStore.loadState(state);

    SonarLintGlobalSettings reloadedState = settingsStore.getState();

    assertThat(reloadedState.isRuleExplicitlyDisabled(RULE1)).isTrue();
    assertThat(reloadedState.isRuleExplicitlyDisabled(RULE)).isFalse();
    assertThat(reloadedState.isRuleExplicitlyDisabled("unknown")).isFalse();

    assertThat(reloadedState.getRulesByKey()).containsOnly(
            entry(RULE, activeRuleWithParam),
            entry(RULE1, inactiveRule)
    );
    assertThat(reloadedState.getRulesByKey().get(RULE).getParams()).containsExactly(entry("paramKey", "paramValue"));
    assertThat(reloadedState.getRulesByKey().get(RULE).isActive()).isTrue();
  }

    @Test
  void testRuleParamAccessors() {
    var settings = new SonarLintGlobalSettings();
    settings.setRuleParam(RULE, PARAM, VALUE);
    assertThat(settings.getRuleParamValue(RULE, PARAM)).isPresent().hasValue(VALUE);
  }

  @Test
  void testRuleIsNotLoaded() {
    var settings = new SonarLintGlobalSettings();
    assertThat(settings.getRuleParamValue(RULE, PARAM).isPresent()).isFalse();
  }

  @Test
  void testDisableRule() {
    var settings = new SonarLintGlobalSettings();
    settings.disableRule(RULE);
    assertThat(settings.isRuleExplicitlyDisabled(RULE)).isTrue();
  }

  @Test
  void testEnableRule() {
    var settings = new SonarLintGlobalSettings();
    settings.enableRule(RULE);
    assertThat(settings.isRuleExplicitlyDisabled(RULE)).isFalse();
  }

  @Test
  void testResetRuleParam() {
    var settings = new SonarLintGlobalSettings();
    settings.setRuleParam(RULE, PARAM, VALUE);
    assertThat(settings.getRuleParamValue(RULE, PARAM)).isPresent().hasValue(VALUE);
    settings.resetRuleParam(RULE, PARAM);
    assertThat(settings.getRuleParamValue(RULE, PARAM)).isEmpty();
  }

  @Test
  void testSetConnections() {
    var settings = new SonarLintGlobalSettings();

    settings.setServerConnections(List.of(ServerConnectionSettings.newBuilder().setHostUrl("host").setName("name").build()));

    assertThat(settings.getServerConnections())
      .extracting(ServerConnectionSettings::getHostUrl)
      .containsOnly("host");
  }
}
