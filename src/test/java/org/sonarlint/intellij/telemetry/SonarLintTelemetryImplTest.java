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
package org.sonarlint.intellij.telemetry;

import com.intellij.util.net.ssl.CertificateManager;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SonarLintTelemetryImplTest extends SonarTest {
  private SonarLintTelemetryImpl telemetry;
  private TelemetryManager engine = mock(TelemetryManager.class);

  @Before
  public void start() throws Exception {
    super.register(CertificateManager.class, mock(CertificateManager.class));
    this.telemetry = createTelemetry();
  }

  @After
  public void after() {
    System.clearProperty(SonarLintTelemetryImpl.DISABLE_PROPERTY_KEY);
  }

  private SonarLintTelemetryImpl createTelemetry() {
    when(engine.isEnabled()).thenReturn(true);

    TelemetryManagerProvider engineProvider = mock(TelemetryManagerProvider.class);
    when(engineProvider.get()).thenReturn(engine);

    SonarLintTelemetryImpl telemetry = new SonarLintTelemetryImpl(engineProvider);
    telemetry.initComponent();
    return telemetry;
  }

  @Test
  public void disable_property_should_disable_telemetry() throws Exception {
    assertThat(createTelemetry().enabled()).isTrue();

    System.setProperty(SonarLintTelemetryImpl.DISABLE_PROPERTY_KEY, "true");
    assertThat(createTelemetry().enabled()).isFalse();
  }

  @Test
  public void stop_should_trigger_stop_telemetry() {
    when(engine.isEnabled()).thenReturn(true);
    telemetry.stop();
    verify(engine).isEnabled();
    verify(engine).stop();
  }

  @Test
  public void test_scheduler() {
    assertThat(telemetry.scheduledFuture).isNotNull();
    assertThat(telemetry.scheduledFuture.getDelay(TimeUnit.MINUTES)).isBetween(0L, 1L);
    telemetry.stop();
    assertThat(telemetry.scheduledFuture).isNull();
  }

  @Test
  public void optOut_should_trigger_disable_telemetry() {
    when(engine.isEnabled()).thenReturn(true);
    telemetry.optOut(true);
    verify(engine).disable();
    telemetry.stop();
  }

  @Test
  public void should_not_opt_out_twice() {
    when(engine.isEnabled()).thenReturn(false);
    telemetry.optOut(true);
    verify(engine).isEnabled();
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void optIn_should_trigger_enable_telemetry() {
    when(engine.isEnabled()).thenReturn(false);
    telemetry.optOut(false);
    verify(engine).enable();
  }

  @Test
  public void upload_should_trigger_upload_when_enabled() {
    when(engine.isEnabled()).thenReturn(true);
    telemetry.upload();
    verify(engine).isEnabled();
    verify(engine).uploadLazily();
  }

  @Test
  public void upload_should_not_trigger_upload_when_disabled() {
    when(engine.isEnabled()).thenReturn(false);
    telemetry.upload();
    verify(engine).isEnabled();
    verifyNoMoreInteractions(engine);
  }

  @Test
  public void usedAnalysis_should_trigger_usedAnalysis_when_enabled() {
    when(engine.isEnabled()).thenReturn(true);
    telemetry.analysisDoneOnMultipleFiles();
    verify(engine).isEnabled();
    verify(engine).analysisDoneOnMultipleFiles();
  }

  @Test
  public void usedAnalysis_should_not_trigger_usedAnalysis_when_disabled() {
    when(engine.isEnabled()).thenReturn(false);
    telemetry.analysisDoneOnMultipleFiles();
    verify(engine).isEnabled();
    verifyNoMoreInteractions(engine);
  }
}
