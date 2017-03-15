/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.net.ssl.CertificateManager;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.telemetry.Telemetry;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarLintTelemetryTest extends SonarTest {
  private SonarLintTelemetry telemetry;
  private Telemetry engine;
  private TelemetryEngineProvider engineProvider;
  private TelemetryClient client;

  @Before
  public void start() throws Exception {
    super.register(CertificateManager.class, mock(CertificateManager.class));
    this.telemetry = createTelemetry();
  }

  @After
  public void after() {
    System.clearProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY);
  }

  private SonarLintTelemetry createTelemetry() throws Exception {
    engine = mock(Telemetry.class);
    client = mock(TelemetryClient.class);
    when(engine.getClient()).thenReturn(client);
    engineProvider = mock(TelemetryEngineProvider.class);
    when(engineProvider.get()).thenReturn(engine);
    ProjectManager projectManager = mock(ProjectManager.class);
    when(projectManager.getOpenProjects()).thenReturn(new Project[0]);
    return new SonarLintTelemetry(engineProvider, projectManager);
  }

  @Test
  public void disable() throws Exception {
    System.setProperty(SonarLintTelemetry.DISABLE_PROPERTY_KEY, "true");
    telemetry = createTelemetry();
    telemetry.initComponent();
    assertThat(telemetry.enabled()).isFalse();
  }

  @Test
  public void testSaveData() {
    telemetry.initComponent();
    telemetry.disposeComponent();
  }

  @Test
  public void testExceptionCreation() throws Exception {
    when(engineProvider.get()).thenThrow(new IOException());
    telemetry.initComponent();
    assertThat(telemetry.enabled()).isFalse();
  }

  @Test
  public void testScheduler() throws IOException {
    telemetry.initComponent();
    assertThat(telemetry.scheduledFuture).isNotNull();
    assertThat(telemetry.scheduledFuture.getDelay(TimeUnit.MINUTES)).isBetween(0L, 1L);
    telemetry.disposeComponent();
    assertThat(telemetry.scheduledFuture).isNull();

    assertThat(telemetry.getComponentName()).isEqualTo("SonarLintTelemetry");
  }

  @Test
  public void testOptOut() throws Exception {
    when(engine.enabled()).thenReturn(true);
    telemetry.initComponent();
    telemetry.optOut(true);
    verify(engine).enable(false);
    verify(client).optOut(any(TelemetryClientConfig.class), anyBoolean());
    telemetry.disposeComponent();
  }

  @Test
  public void testDontOptOutAgain() {
    when(engine.enabled()).thenReturn(false);
    telemetry.initComponent();
    telemetry.optOut(true);
    verify(engine).enabled();
    verifyNoMoreInteractions(engine);
    verifyZeroInteractions(client);
  }
}
