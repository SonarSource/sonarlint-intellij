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

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class TelemetryStorageTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private LocalDate today = LocalDate.now();
  private Path filePath;

  @Before
  public void setUp() throws IOException {
    filePath = temp.newFolder().toPath().resolve("file");
  }

  @Test
  public void loadSaveDefault() throws IOException {
    TelemetryStorage storage = new TelemetryStorage();
    storage.save(filePath);

    assertThat(filePath).exists();
    storage = TelemetryStorage.load(filePath);
    assertThat(storage.installDate()).isEqualTo(today);
    assertThat(storage.lastUseDate()).isNull();
    assertThat(storage. numUseDays()).isEqualTo(0);
    assertThat(storage.enabled()).isTrue();
  }

  @Test
  public void setData() throws IOException {
    createAndSave(today, today, 1L, true);

    assertThat(filePath).exists();
    TelemetryStorage storage = TelemetryStorage.load(filePath);
    assertThat(storage.installDate()).isEqualTo(today);
    assertThat(storage.lastUseDate()).isEqualTo(today);
    assertThat(storage. numUseDays()).isEqualTo(1L);
    assertThat(storage.enabled()).isTrue();
  }

  @Test
  public void fixInvalidData() throws IOException {
    // null installation date
    createAndSave(null, null, 100L, true);
    TelemetryStorage storage = TelemetryStorage.load(filePath);
    assertThat(storage.installDate()).isEqualTo(today);
    assertThat(storage.lastUseDate()).isNull();
    assertThat(storage. numUseDays()).isEqualTo(0L);

    // too many days used
    createAndSave(LocalDate.of(2017, 1, 1), LocalDate.of(2017, 1, 7), 100L, true);
    storage = TelemetryStorage.load(filePath);
    assertThat(storage.installDate()).isEqualTo(LocalDate.of(2017, 1, 1));
    assertThat(storage.lastUseDate()).isEqualTo(today);
    assertThat(storage. numUseDays()).isEqualTo(7L);
    assertThat(storage.enabled()).isTrue();

    // dates in future (will this test fail one day?)
    createAndSave(LocalDate.of(3000, 1, 1), LocalDate.of(3000, 1, 7), 100L, false);
    storage = TelemetryStorage.load(filePath);
    assertThat(storage.installDate()).isEqualTo(today);
    assertThat(storage.lastUseDate()).isEqualTo(today);
    assertThat(storage. numUseDays()).isEqualTo(1L);
    assertThat(storage.enabled()).isFalse();
  }

  private TelemetryStorage createAndSave(LocalDate install, LocalDate lastUsed, long numDaysUsed, boolean enabled) throws IOException {
    TelemetryStorage storage = new TelemetryStorage();
    storage.setEnabled(enabled);
    storage.setNumUseDays(numDaysUsed);
    storage.setInstallDate(install);
    storage.setLastUseDate(lastUsed);
    storage.save(filePath);
    return storage;
  }
}
