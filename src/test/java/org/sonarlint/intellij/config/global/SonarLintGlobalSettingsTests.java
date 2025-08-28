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

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class SonarLintGlobalSettingsTests extends AbstractSonarLintLightTests {

  private static final int EXPECTED_NON_STATIC_FIELD_COUNT = 13;
  private static final String RULE = "rule";
  private static final String RULE1 = "rule1";
  private static final String PARAM = "param";
  private static final String VALUE = "value";

  @Test
  void testRoundTrip() {
    var settings = new SonarLintGlobalSettings();
    assertThat(settings.isAutoTrigger()).isTrue();
    assertThat(settings.getNodejsPath()).isBlank();

    var server = ServerConnection.newBuilder().setName("name").build();

    settings.setServerConnections(List.of(server));
    assertThat(settings.getServerConnections()).containsOnly(server);

    settings.setAutoTrigger(false);
    assertThat(settings.isAutoTrigger()).isFalse();

    settings.setNodejsPath("path/to/node");
    assertThat(settings.getNodejsPath()).isEqualTo("path/to/node");
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
    assertThat(settings.getRuleParamValue(RULE, PARAM)).isNotPresent();
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
  void testAddConnection() {
    var settings = new SonarLintGlobalSettings();

    settings.addServerConnection(ServerConnection.newBuilder().setHostUrl("host").setName("name").build());

    assertThat(settings.getServerConnections())
      .extracting(ServerConnection::getHostUrl)
      .containsOnly("host");
  }

  @Test
  void getConnectionTo_should_ignore_trailing_slashes() {
    var settings = new SonarLintGlobalSettings();
    settings.addServerConnection(ServerConnection.newBuilder().setHostUrl("http://host/").setName("name").build());

    assertThat(settings.getConnectionsTo("http://host"))
      .extracting(ServerConnection::getName)
      .containsOnly("name");
  }

  @Test
  void testCopyConstructorHasSameFieldSize() {
    var declaredFields = SonarLintGlobalSettings.class.getDeclaredFields();
    var nonStaticFieldCount = 0;
    for (var field : declaredFields) {
      if (!Modifier.isStatic(field.getModifiers())) {
        nonStaticFieldCount++;
      }
    }

    assertThat(nonStaticFieldCount).isEqualTo(EXPECTED_NON_STATIC_FIELD_COUNT);
  }

  @Test
  void testCopyConstructorCopiesProperly() {
    var original = new SonarLintGlobalSettings();
    original.setFocusOnNewCode(true);
    original.setPromotionDisabled(true);
    original.setAutoTrigger(false);
    original.setRegionEnabled(true);
    original.setNodejsPath("/usr/local/node");
    original.setHasWalkthroughRunOnce(true);
    original.setSecretsNeverBeenAnalysed(false);
    original.setServerConnections(List.of(
      ServerConnection.newBuilder().setName("test-server").setHostUrl("http://localhost:9000").build()
    ));
    original.setFileExclusions(List.of("**/test/**"));
    original.setRules(List.of());
    original.setDefaultSortMode("SEVERITY");

    var copy = new SonarLintGlobalSettings(original);

    assertThat(copy.isFocusOnNewCode()).isEqualTo(original.isFocusOnNewCode());
    assertThat(copy.isPromotionDisabled()).isEqualTo(original.isPromotionDisabled());
    assertThat(copy.isAutoTrigger()).isEqualTo(original.isAutoTrigger());
    assertThat(copy.isRegionEnabled()).isEqualTo(original.isRegionEnabled());
    assertThat(copy.getNodejsPath()).isEqualTo(original.getNodejsPath());
    assertThat(copy.hasWalkthroughRunOnce()).isEqualTo(original.hasWalkthroughRunOnce());
    assertThat(copy.isSecretsNeverBeenAnalysed()).isEqualTo(original.isSecretsNeverBeenAnalysed());
    assertThat(copy.getServerConnections()).isEqualTo(original.getServerConnections());
    assertThat(copy.getFileExclusions()).isEqualTo(original.getFileExclusions());
    assertThat(copy.getRules()).isEqualTo(original.getRules());
    assertThat(copy.getRulesByKey()).isEqualTo(original.getRulesByKey());
    assertThat(copy.getDefaultSortMode()).isEqualTo(original.getDefaultSortMode());
  }

  @Test
  void testDeepCopyOfServerConnections() {
    var original = new SonarLintGlobalSettings();
    original.setServerConnections(List.of(
      ServerConnection.newBuilder()
        .setName("test-server")
        .setHostUrl("http://localhost:9000")
        .build()
    ));

    var copy = new SonarLintGlobalSettings(original);
    var modifiedConnection = ServerConnection.newBuilder()
      .setName("modified-server")
      .setHostUrl("http://localhost:9000")
      .build();
    copy.setServerConnections(List.of(modifiedConnection));

    assertThat(original.getServerConnections()).hasSize(1);
    assertThat(original.getServerConnections().get(0).getName()).isEqualTo("test-server");
    assertThat(copy.getServerConnections()).hasSize(1);
    assertThat(copy.getServerConnections().get(0).getName()).isEqualTo("modified-server");
  }
}
