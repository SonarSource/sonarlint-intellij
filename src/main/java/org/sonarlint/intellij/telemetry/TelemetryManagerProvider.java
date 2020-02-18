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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.net.ssl.CertificateManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

public class TelemetryManagerProvider {
  private static final String TELEMETRY_PRODUCT_KEY = "idea";
  private static final String PRODUCT = "SonarLint IntelliJ";

  private static final String OLD_STORAGE_FILENAME = "sonarlint_usage";

  private final SonarApplication application;
  private final ProjectManager projectManager;

  public TelemetryManagerProvider(SonarApplication application, ProjectManager projectManager) {
    this.application = application;
    this.projectManager = projectManager;
  }

  public TelemetryManager get() {
    TelemetryClientConfig clientConfig = getTelemetryClientConfig();
    TelemetryClient client = new TelemetryClient(clientConfig, PRODUCT, application.getVersion(), SonarLintUtils.getIdeVersionForTelemetry());
    return new TelemetryManager(getStorageFilePath(), client, this::isAnyProjectConnected, this::isAnyProjectConnectedToSonarCloud);
  }

  private static TelemetryClientConfig getTelemetryClientConfig() {
    CertificateManager certificateManager = CertificateManager.getInstance();
    TelemetryClientConfig.Builder clientConfigBuilder = new TelemetryClientConfig.Builder()
      .userAgent("SonarLint")
      .sslSocketFactory(certificateManager.getSslContext().getSocketFactory())
      .sslTrustManager(certificateManager.getCustomTrustManager());

    SonarLintUtils.configureProxy(TelemetryManager.TELEMETRY_ENDPOINT, clientConfigBuilder);

    return clientConfigBuilder.build();
  }

  @VisibleForTesting
  Path getStorageFilePath() {
    TelemetryPathManager.migrate(TELEMETRY_PRODUCT_KEY, getOldStorageFilePath());
    return TelemetryPathManager.getPath(TELEMETRY_PRODUCT_KEY);
  }

  private static Path getOldStorageFilePath() {
    return Paths.get(PathManager.getSystemPath()).resolve(OLD_STORAGE_FILENAME);
  }

  private boolean isAnyProjectConnected() {
    Project[] openProjects = projectManager.getOpenProjects();
    return Arrays.stream(openProjects).anyMatch(p -> SonarLintUtils.get(p, SonarLintProjectSettings.class).isBindingEnabled());
  }

  private boolean isAnyProjectConnectedToSonarCloud() {
    Project[] openProjects = projectManager.getOpenProjects();
    return Arrays.stream(openProjects).anyMatch(p -> {
      try {
        ProjectBindingManager bindingManager = SonarLintUtils.get(p, ProjectBindingManager.class);
        return bindingManager.getSonarQubeServer().isSonarCloud();
      } catch (InvalidBindingException e) {
        return false;
      }
    });
  }
}
