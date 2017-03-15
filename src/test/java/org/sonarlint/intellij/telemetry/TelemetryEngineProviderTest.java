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
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarApplication;
import org.sonarsource.sonarlint.core.telemetry.Telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class TelemetryEngineProviderTest {
  private TelemetryEngineProvider engineProvider;
  private Path filePath;

  @Before
  public void before() {
    filePath = Paths.get(PathManager.getSystemPath(), "sonarlint_usage");
    engineProvider = new TelemetryEngineProvider(mock(SonarApplication.class));
  }

  @Test
  public void testCreation() throws Exception {
    Telemetry telemetry = engineProvider.get();
    telemetry.save();
    assertThat(filePath).exists();
  }
}
