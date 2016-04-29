/**
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
package org.sonarlint.intellij.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;

public class CreateTokenTask extends com.intellij.openapi.progress.Task.Modal {
  private static final Logger LOGGER = Logger.getInstance(ConnectionTestTask.class);
  private static final String TOKEN_NAME = "SonarLint_IntelliJ_automatically_generated_";
  private String host;
  private String name;
  private String login;
  private String password;

  private String token;
  private Exception exception;

  public CreateTokenTask(String host, String name, String login, String password) {
    super(null, "Create token", true);
    this.host = host;
    this.name = name;
    this.login = login;
    this.password = password;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    SonarApplication sonarlint = ApplicationManager.getApplication().getComponent(SonarApplication.class);
    indicator.setText("Creating token with " + host + "...");
    indicator.setIndeterminate(true);

    try {
      ServerConfiguration.Builder serverConfigBuilder = ServerConfiguration.builder()
        .userAgent("SonarLint IntelliJ " + sonarlint.getVersion())
        .connectTimeoutMilliseconds(SonarLintUtils.CONNECTION_TIMEOUT_MS)
        .readTimeoutMilliseconds(SonarLintUtils.CONNECTION_TIMEOUT_MS)
        .url(host)
        .credentials(login, password);
      ServerConfiguration serverConfig = serverConfigBuilder.build();

      WsHelper wsHelper = new WsHelperImpl();
      String tokenName = TOKEN_NAME + name;
      token = wsHelper.generateAuthenticationToken(serverConfig, tokenName, true);
    } catch (Exception e) {
      LOGGER.info("Creation of token failed", e);
      exception = e;
    }
  }

  public String getToken() {
    return token;
  }

  public Exception getException() {
    return exception;
  }

}
