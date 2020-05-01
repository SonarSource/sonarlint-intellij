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

import com.intellij.configurationStore.DefaultStateSerializerKt;
import com.intellij.idea.MainImpl;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


public class SonarLintGlobalSettingsTest extends SonarTest {

  private static final String RULE = "rule";
  private static final String RULE1 = "rule1";
  private static final String PARAM = "param";
  private static final String VALUE = "value";

  @Test
  public void testRoundTrip() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    SonarQubeServer server = SonarQubeServer.newBuilder().setName("name").build();

    settings.setSonarQubeServers(Collections.singletonList(server));
    assertThat(settings.getSonarQubeServers()).containsOnly(server);

    settings.setAutoTrigger(true);
    assertThat(settings.isAutoTrigger()).isTrue();

    assertThat(settings.getComponentName()).isEqualTo("SonarLintGlobalSettings");
    assertThat(settings.getPresentableName()).isEqualTo("SonarLint settings");

    assertThat(settings.getState()).isEqualTo(settings);
    assertThat(settings.getExportFiles()).isNotEmpty();
  }

  @Test
  public void testGetInstance() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    super.register(app, SonarLintGlobalSettings.class, settings);
    assertThat(SonarLintGlobalSettings.getInstance()).isEqualTo(settings);

  }

  @Test
  public void testLoadState() throws Exception {
    SonarLintGlobalSettings state = new SonarLintGlobalSettings();
    HashSet<String> includedRules = new HashSet<>();
    includedRules.add(RULE);
    HashSet<String> excludedRules = new HashSet<>();
    excludedRules.add(RULE1);
    Field includedRulesField = state.getClass().getDeclaredField("includedRules");
    includedRulesField.setAccessible(true);
    includedRulesField.set(state, includedRules);
    Field rulesField = state.getClass().getDeclaredField("rules");
    rulesField.setAccessible(true);
    Field excludedRulesField = SonarLintGlobalSettings.class.getDeclaredField("excludedRules");
    excludedRulesField.setAccessible(true);
    excludedRulesField.set(state, excludedRules);
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();

    settings.loadState(state);

    Map<String, SonarLintGlobalSettings.Rule> rules = (Map<String, SonarLintGlobalSettings.Rule>) rulesField.get(settings);
    assertThat(rules).containsKey(RULE);
    assertThat(rules).containsKey(RULE1);
    assertThat(rules.get(RULE).isActive).isTrue();
    assertThat(rules.get(RULE1).isActive).isFalse();

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
