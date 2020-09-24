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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintMockedTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class SonarLintGlobalSettingsTest extends AbstractSonarLintMockedTests {

  private static final String RULE = "rule";
  private static final String RULE1 = "rule1";
  private static final String PARAM = "param";
  private static final String VALUE = "value";

  @Test
  public void testRoundTrip() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    assertThat(settings.isAutoTrigger()).isTrue();
    assertThat(settings.getNodejsPath()).isBlank();

    SonarQubeServer server = SonarQubeServer.newBuilder().setName("name").build();

    settings.setSonarQubeServers(Collections.singletonList(server));
    assertThat(settings.getSonarQubeServers()).containsOnly(server);

    settings.setAutoTrigger(false);
    assertThat(settings.isAutoTrigger()).isFalse();

    settings.setNodejsPath("path/to/node");
    assertThat(settings.getNodejsPath()).isEqualTo("path/to/node");
  }

  @Test
  public void testLoadStateOldFormat() {
    SonarLintGlobalSettings state = new SonarLintGlobalSettings();
    HashSet<String> includedRules = new HashSet<>();
    includedRules.add(RULE);
    HashSet<String> excludedRules = new HashSet<>();
    excludedRules.add(RULE1);
    state.setIncludedRules(includedRules);
    state.setExcludedRules(excludedRules);

    SonarLintGlobalSettingsStore settingsStore = new SonarLintGlobalSettingsStore();
    settingsStore.loadState(state);

    Map<String, SonarLintGlobalSettings.Rule> rules = state.getRulesByKey();
    assertThat(rules).containsOnlyKeys(RULE, RULE1);
    assertThat(rules.get(RULE).isActive).isTrue();
    assertThat(rules.get(RULE1).isActive).isFalse();

  }

  @Test
  public void testLoadStateNewFormat() {
    SonarLintGlobalSettings state = new SonarLintGlobalSettings();
    SonarLintGlobalSettingsStore settingsStore = new SonarLintGlobalSettingsStore();
    SonarLintGlobalSettings.Rule activeRuleWithParam = new SonarLintGlobalSettings.Rule(RULE, true);
    activeRuleWithParam.setParams(Collections.singletonMap("paramKey", "paramValue"));
    SonarLintGlobalSettings.Rule inactiveRule = new SonarLintGlobalSettings.Rule(RULE1, false);
    state.setRules(Arrays.asList(activeRuleWithParam, inactiveRule));

    settingsStore.loadState(state);

    assertThat(settingsStore.getState().isRuleExplicitlyDisabled(RULE1)).isTrue();
    assertThat(settingsStore.getState().isRuleExplicitlyDisabled(RULE)).isFalse();
    assertThat(settingsStore.getState().isRuleExplicitlyDisabled("unknown")).isFalse();

    assertThat(settingsStore.getState().getRulesByKey()).containsOnly(
            entry(RULE, activeRuleWithParam),
            entry(RULE1, inactiveRule)
    );
    assertThat(settingsStore.getState().getRulesByKey().get(RULE).getParams()).containsExactly(entry("paramKey", "paramValue"));
    assertThat(settingsStore.getState().getRulesByKey().get(RULE).isActive()).isTrue();
  }

    @Test
  public void testRuleParamAccessors() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    settings.setRuleParam(RULE, PARAM, VALUE);
    assertThat(settings.getRuleParamValue(RULE, PARAM)).isPresent().hasValue(VALUE);
  }

  @Test
  public void testRuleIsNotLoaded() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    assertThat(settings.getRuleParamValue(RULE, PARAM).isPresent()).isFalse();
  }

  @Test
  public void testDisableRule() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    settings.disableRule(RULE);
    assertThat(settings.isRuleExplicitlyDisabled(RULE)).isTrue();
  }

  @Test
  public void testEnableRule() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    settings.enableRule(RULE);
    assertThat(settings.isRuleExplicitlyDisabled(RULE)).isFalse();
  }

  @Test
  public void testResetRuleParam() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    settings.setRuleParam(RULE, PARAM, VALUE);
    assertThat(settings.getRuleParamValue(RULE, PARAM)).isPresent().hasValue(VALUE);
    settings.resetRuleParam(RULE, PARAM);
    assertThat(settings.getRuleParamValue(RULE, PARAM)).isEmpty();
  }
}
