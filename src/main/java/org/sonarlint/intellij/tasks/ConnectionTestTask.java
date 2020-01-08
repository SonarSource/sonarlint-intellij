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
package org.sonarlint.intellij.tasks;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;

public class ConnectionTestTask extends Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(ConnectionTestTask.class);
  private final SonarQubeServer server;
  private Exception exception;
  private ValidationResult result;

  public ConnectionTestTask(SonarQubeServer server) {
    super(null, "Test Connection to SonarQube Server", true);
    this.server = server;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText("Connecting to " + server.getHostUrl() + "...");
    indicator.setIndeterminate(true);

    try {
      ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
      WsHelper wsHelper = new WsHelperImpl();
      result = wsHelper.validateConnection(serverConfiguration);
    } catch (Exception e) {
      String msg = "Connection test failed";
      LOGGER.info(msg, e);
      GlobalLogOutput.get().logError(msg, e);
      exception = e;
    }
  }

  public Exception getException() {
    return exception;
  }

  public ValidationResult result() {
    return result;
  }

}


