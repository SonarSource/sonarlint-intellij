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
import org.sonarlint.intellij.SonarApplication;
import org.sonarsource.sonarlint.core.telemetry.Telemetry;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

public class TelemetryEngineProvider {
  private static final String TELEMETRY_PRODUCT_KEY = "idea";
  private static final String PRODUCT = "SonarLint IntelliJ";

  private static final String OLD_STORAGE_FILENAME = "sonarlint_usage";

  private final SonarApplication application;

  public TelemetryEngineProvider(SonarApplication application) {
    this.application = application;
  }

  public Telemetry get() throws Exception {
    return new Telemetry(getStorageFilePath(), PRODUCT, application.getVersion());
  }

  static Path getStorageFilePath() {
    TelemetryPathManager.migrate(TELEMETRY_PRODUCT_KEY, getOldStorageFilePath());
    return TelemetryPathManager.getPath(TELEMETRY_PRODUCT_KEY);
  }

  private static Path getOldStorageFilePath() {
    return Paths.get(PathManager.getSystemPath()).resolve(OLD_STORAGE_FILENAME);
  }
}
