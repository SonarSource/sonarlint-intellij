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
package org.sonarlint.intellij.telemetry;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SonarLintTelemetryImplTests extends AbstractSonarLintLightTests {
  private SonarLintTelemetryImpl telemetry;
  private final TelemetryManager telemetryManager = mock(TelemetryManager.class);

  @BeforeEach
  void start() {
    System.clearProperty(SonarLintTelemetryImpl.DISABLE_PROPERTY_KEY);
    this.telemetry = createTelemetry();
  }

  @AfterAll
  static void restoreSystemProp() {
    System.setProperty(SonarLintTelemetryImpl.DISABLE_PROPERTY_KEY, "true");
  }

  private SonarLintTelemetryImpl createTelemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);

    var engineProvider = mock(TelemetryManagerProvider.class);
    when(engineProvider.get()).thenReturn(telemetryManager);

    var telemetry = new SonarLintTelemetryImpl(engineProvider);
    telemetry.init();
    return telemetry;
  }

  @Test
  void disable_property_should_disable_telemetry() throws Exception {
    assertThat(createTelemetry().enabled()).isTrue();

    System.setProperty(SonarLintTelemetryImpl.DISABLE_PROPERTY_KEY, "true");
    assertThat(createTelemetry().enabled()).isFalse();
  }

  @Test
  void stop_should_trigger_stop_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.stop();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).stop();
  }

  @Test
  void test_scheduler() {
    assertThat((Object)telemetry.scheduledFuture).isNotNull();
    assertThat(telemetry.scheduledFuture.getDelay(TimeUnit.MINUTES)).isBetween(0L, 1L);
    telemetry.stop();
    assertThat((Object)telemetry.scheduledFuture).isNull();
  }

  @Test
  void optOut_should_trigger_disable_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.optOut(true);
    verify(telemetryManager).disable();
    telemetry.stop();
  }

  @Test
  void should_not_opt_out_twice() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.optOut(true);
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void optIn_should_trigger_enable_telemetry() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.optOut(false);
    verify(telemetryManager).enable();
  }

  @Test
  void upload_should_trigger_upload_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.upload();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).uploadLazily();
  }

  @Test
  void upload_should_not_trigger_upload_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.upload();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void usedAnalysis_should_trigger_usedAnalysis_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    telemetry.analysisDoneOnMultipleFiles();
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).analysisDoneOnMultipleFiles();
  }

  @Test
  void usedAnalysis_should_not_trigger_usedAnalysis_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.analysisDoneOnMultipleFiles();
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }

  @Test
  void addQuickFixAppliedForRule_should_trigger_addQuickFixAppliedForRule_when_enabled() {
    when(telemetryManager.isEnabled()).thenReturn(true);
    String ruleKey = "repo:key";
    telemetry.addQuickFixAppliedForRule(ruleKey);
    verify(telemetryManager).isEnabled();
    verify(telemetryManager).addQuickFixAppliedForRule(ruleKey);
  }

  @Test
  void addQuickFixAppliedForRule_should_not_trigger_addQuickFixAppliedForRule_when_disabled() {
    when(telemetryManager.isEnabled()).thenReturn(false);
    telemetry.addQuickFixAppliedForRule("repo:key");
    verify(telemetryManager).isEnabled();
    verifyNoMoreInteractions(telemetryManager);
  }
}
