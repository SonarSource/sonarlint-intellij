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
package org.sonarlint.intellij.core;

import com.intellij.openapi.application.ApplicationManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.Version;

public class NodeJsManager {

  private boolean nodeInit = false;
  private Path nodeJsPath = null;
  private Version nodeJsVersion = null;

  public NodeJsManager() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override public void applied(SonarLintGlobalSettings settings) {
        if (!Objects.equals(Paths.get(settings.getNodejsPath()), nodeJsPath)) {
          clear();
          // Node.js path is passed at engine startup, so we have to restart them all to ensure the new value is taken into account
          SonarLintUtils.getService(SonarLintEngineManager.class).stopAllEngines();
        }
      }
    });
  }

  private synchronized void clear() {
    this.nodeInit = false;
    this.nodeJsPath = null;
    this.nodeJsVersion = null;
  }

  private synchronized void initNodeIfNeeded() {
    if (!nodeInit) {
      NodeJsHelper helper = new NodeJsHelper();
      helper.detect(getNodeJsPathFromConfig());
      this.nodeInit = true;
      this.nodeJsPath = helper.getNodeJsPath();
      this.nodeJsVersion = helper.getNodeJsVersion();
    }
  }

  public Path getNodeJsPath() {
    initNodeIfNeeded();
    return nodeJsPath;
  }

  public Version getNodeJsVersion() {
    initNodeIfNeeded();
    return nodeJsVersion;
  }

  @CheckForNull
  private static Path getNodeJsPathFromConfig() {
    final String nodejsPathStr = Settings.getGlobalSettings().getNodejsPath();
    if (StringUtils.isNotBlank(nodejsPathStr)) {
      try {
        return Paths.get(nodejsPathStr);
      } catch (Exception e) {
        throw new IllegalStateException("Invalid Node.js path", e);
      }
    }
    return null;
  }

}
