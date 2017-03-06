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
import java.time.LocalDate;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryData;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.sonarlint.intellij.util.SonarLintUtils.getTelemetryClientConfig;

public class TelemetryClient {
  private static final String PRODUCT = "SonarLint IntelliJ";
  private final WsHelper wsHelper;
  private final SonarApplication application;
  private final ProjectManager projectManager;

  public TelemetryClient(WsHelper wsHelper, SonarApplication application, ProjectManager projectManager) {
    this.wsHelper = wsHelper;
    this.application = application;
    this.projectManager = projectManager;
  }

  public void run(TelemetryStorage telemetryStorage) {
    try {
      String version = application.getVersion();
      boolean connected = isAnyProjectConnected();
      sendData(wsHelper, telemetryStorage, version, connected);
    } catch (Exception e) {
      // fail silently
      e.printStackTrace();
    }
  }

  private static void sendData(WsHelper helper, TelemetryStorage telemetry, String version, boolean connected) {
    long daysSinceInstallation = telemetry.installDate().until(LocalDate.now(), DAYS);

    TelemetryData data = new TelemetryData(daysSinceInstallation, telemetry.numUseDays(), PRODUCT, version, connected);
    TelemetryClientConfig clientConfig = getTelemetryClientConfig();

    helper.sendTelemetryData(clientConfig, data);
  }

  private boolean isAnyProjectConnected() {
    Project[] openProjects = projectManager.getOpenProjects();

    for (Project p : openProjects) {
      SonarLintProjectSettings projectSettings = SonarLintUtils.get(p, SonarLintProjectSettings.class);
      if (projectSettings.isBindingEnabled()) {
        return true;
      }
    }
    return false;
  }


}
