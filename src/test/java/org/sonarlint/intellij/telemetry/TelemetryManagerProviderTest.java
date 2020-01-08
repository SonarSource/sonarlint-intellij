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

import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.net.ssl.CertificateManager;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.SonarTest;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class TelemetryManagerProviderTest extends SonarTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void start() {
    super.register(CertificateManager.class, mock(CertificateManager.class));
  }

  @Test
  public void testCreation() throws Exception {
    Path path = temporaryFolder.newFolder().toPath().resolve("usage");

    TelemetryManagerProvider engineProvider = new TelemetryManagerProvider(mock(SonarApplication.class), mock(ProjectManager.class)) {
      @Override
      Path getStorageFilePath() {
        return path;
      }
    };

    TelemetryManager telemetry = engineProvider.get();
    assertThat(path).doesNotExist();
    telemetry.analysisDoneOnMultipleFiles();
    assertThat(path).exists();
  }
}
