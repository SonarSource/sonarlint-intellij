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

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.ProjectManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarApplication;
import org.sonarsource.sonarlint.core.WsHelperImpl;

public class SonarLintTelemetry implements ApplicationComponent {
  private static final String STORAGE_FILENAME = "sonarlint_usage.json";
  private final TelemetryClient worker;
  private TelemetryStorage storage;
  private ScheduledFuture<?> scheduledFuture;

  public SonarLintTelemetry(SonarApplication application, ProjectManager projectManager) {
    this.worker = new TelemetryClient(new WsHelperImpl(), application, projectManager);
  }

  private boolean loadStorage() {
    Path filePath = getStorageFilePath();
    storage = null;

    if (Files.exists(filePath)) {
      try {
        storage = TelemetryStorage.load(filePath);
      } catch (Exception e) {
        // ignore, we will recreate
      }
    }

    if (storage == null) {
      storage = new TelemetryStorage();
      try {
        storage.save(filePath);
      } catch (IOException e) {
        // no data should be sent
        return false;
      }
    }
    return true;
  }

  public boolean enabled() {
    return storage.enabled();
  }

  public void setEnabled(boolean enabled) {
    storage.setEnabled(enabled);
    saveData();
  }

  @Override public void initComponent() {
    try {
      if (loadStorage()) {
        scheduleUpload();
      }
    } catch (Exception e) {
      // fail silently
    }
  }

  private void scheduleUpload() {
    scheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(this::upload,
      1, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
  }

  private void upload() {
    if (!storage.enabled()) {
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime lastUpload = storage.lastUploadDateTime();
    if (lastUpload == null || lastUpload.until(now, ChronoUnit.HOURS) >= 23) {
      scheduledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> worker.run(storage),
        1, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
      storage.setLastUploadTime(now);
      saveData();
    }
  }

  public void markUsage() {
    LocalDate today = LocalDate.now();
    LocalDate lastUsed = storage.lastUseDate();
    if (lastUsed == null || !lastUsed.equals(today)) {
      storage.setLastUseDate(today);
      storage.setNumUseDays(storage.numUseDays() + 1);
      saveData();
    }
  }

  @Override public void disposeComponent() {
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
      scheduledFuture = null;
    }
    saveData();
  }

  private void saveData() {
    try {
      storage.save(getStorageFilePath());
    } catch (IOException e) {
      // fail silently
    }
  }

  @NotNull @Override public String getComponentName() {
    return "SonarLintTelemetry";
  }

  private static Path getStorageFilePath() {
    return Paths.get(PathManager.getSystemPath()).resolve(STORAGE_FILENAME);
  }

}
