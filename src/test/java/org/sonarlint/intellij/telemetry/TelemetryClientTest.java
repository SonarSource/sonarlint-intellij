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
import java.io.IOException;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryData;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TelemetryClientTest extends SonarTest {
  private WsHelper wsHelper;
  private ProjectManager projectManager;
  private SonarApplication sonarApp;
  private SonarLintProjectSettings projectSettings;

  @Before
  public void prepare() throws IOException {
    wsHelper = mock(WsHelper.class);
    sonarApp = mock(SonarApplication.class);
    projectManager = mock(ProjectManager.class);
    projectSettings = new SonarLintProjectSettings();
    when(projectManager.getOpenProjects()).thenReturn(new Project[] {project});

    super.register(SonarLintProjectSettings.class, projectSettings);
    super.register(app, SonarApplication.class, sonarApp);
  }

  @Test
  public void testExecution() throws IOException {
    TelemetryStorage storage = new TelemetryStorage();
    storage.setInstallDate(LocalDate.of(2017, 3, 3));
    storage.setLastUseDate(LocalDate.of(2017, 4, 4));
    storage.setNumUseDays(10L);

    TelemetryClient worker = new TelemetryClient(wsHelper, sonarApp, projectManager);
    worker.run(storage);
    verify(wsHelper).sendTelemetryData(any(TelemetryClientConfig.class), any(TelemetryData.class));
  }

  @Test
  public void failSilently() {
    TelemetryStorage storage = new TelemetryStorage();
    storage.setInstallDate(LocalDate.of(2017, 3, 3));
    storage.setLastUseDate(LocalDate.of(2017, 4, 4));
    storage.setNumUseDays(10L);

    when(wsHelper.sendTelemetryData(any(TelemetryClientConfig.class), any(TelemetryData.class))).thenThrow(new IllegalArgumentException("failed"));
    TelemetryClient worker = new TelemetryClient(wsHelper, sonarApp, projectManager);
    worker.run(storage);
  }
}
