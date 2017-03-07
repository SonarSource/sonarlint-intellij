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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.ProjectManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SonarLintTelemetryTest {
  private SonarLintTelemetry telemetry;
  private Path filePath;

  @Before
  public void setUp() throws IOException {
    telemetry = createTelemetry();
    filePath = Paths.get(PathManager.getSystemPath(), "sonarlint_usage");
    Files.deleteIfExists(filePath);
  }

  private SonarLintTelemetry createTelemetry() {
    SonarApplication app = mock(SonarApplication.class);
    ProjectManager projectManager = mock(ProjectManager.class);
    return new SonarLintTelemetry(app, projectManager);
  }

  @Test
  public void testSaveData() {
    telemetry.initComponent();
    telemetry.disposeComponent();
    assertThat(filePath).exists();
  }

  @Test
  public void testInvalidFile() throws IOException {
    Files.write(filePath, "trash".getBytes(StandardCharsets.UTF_8));
    telemetry.initComponent();
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
  public void testDisable() {
    telemetry.initComponent();
    assertThat(telemetry.telemetry.enabled()).isTrue();
    telemetry.setEnabled(false);
    assertThat(telemetry.telemetry.enabled()).isFalse();
    telemetry.disposeComponent();

    telemetry = createTelemetry();
    telemetry.initComponent();
    assertThat(telemetry.telemetry.enabled()).isFalse();
    telemetry.disposeComponent();
  }
}
