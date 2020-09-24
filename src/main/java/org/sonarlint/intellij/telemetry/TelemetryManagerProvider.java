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
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.SonarLintPlugin;
import org.sonarlint.intellij.core.NodeJsManager;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;
import org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class TelemetryManagerProvider {
  private static final String TELEMETRY_PRODUCT_KEY = "idea";
  private static final String PRODUCT = "SonarLint IntelliJ";

  private static final String OLD_STORAGE_FILENAME = "sonarlint_usage";


  public TelemetryManager get() {
    TelemetryClientConfig clientConfig = getTelemetryClientConfig();
    SonarLintPlugin plugin = SonarLintUtils.getService(SonarLintPlugin.class);
    TelemetryClient client = new TelemetryClient(clientConfig, PRODUCT, plugin.getVersion(), SonarLintUtils.getIdeVersionForTelemetry());
    return new TelemetryManager(getStorageFilePath(), client, this::isAnyProjectConnected, this::isAnyProjectConnectedToSonarCloud, this::getNodeJsVersion);
  }

  @CheckForNull
  private String getNodeJsVersion() {
    final Version nodeJsVersion = SonarLintUtils.getService(NodeJsManager.class).getNodeJsVersion();
    if (nodeJsVersion != null) {
      return nodeJsVersion.toString();
    }
    return null;
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
    ProjectManager projectManager = ProjectManager.getInstance();
    Project[] openProjects = projectManager.getOpenProjects();
    return Arrays.stream(openProjects).anyMatch(p -> getSettingsFor(p).isBindingEnabled());
  }

  private boolean isAnyProjectConnectedToSonarCloud() {
    ProjectManager projectManager = ProjectManager.getInstance();
    Project[] openProjects = projectManager.getOpenProjects();
    return Arrays.stream(openProjects).anyMatch(p -> {
      try {
        ProjectBindingManager bindingManager = SonarLintUtils.getService(p, ProjectBindingManager.class);
        return bindingManager.getSonarQubeServer().isSonarCloud();
      } catch (InvalidBindingException e) {
        return false;
      }
    });
  }
}
