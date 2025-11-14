/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.config.global.wizard;

import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.config.global.credentials.CredentialsService;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionWizardModelTests {

  @Test
  void testCreateFromConfig() {
    var server = ServerConnection.newBuilder()
      .setName("name")
      .setOrganizationKey("org")
      .setEnableProxy(true)
      .setHostUrl("url")
      .build();

    var model = new ConnectionWizardModel(server, credentialsServiceMock(true).getCredentials(server));
    assertThat(model.getLogin()).isNull();
    assertThat(model.getPassword()).isNull();
    assertThat(model.getToken()).isEqualTo("token");
    assertThat(model.getOrganizationKey()).isEqualTo("org");
    assertThat(model.getOrganizationList()).isEmpty();
    assertThat(model.getName()).isEqualTo("name");
    assertThat(model.getServerUrl()).isEqualTo("url");
  }

  @Test
  void testExportToConfig() {
    var model = new ConnectionWizardModel();
    model.setName("name");
    model.setOrganizationKey("org");
    model.setServerUrl("url");
    model.setLogin("login");
    model.setProxyEnabled(true);
    model.setPassword(new char[] {'p', 'a', 's', 's'});

    model.setServerType(ConnectionWizardModel.ServerType.SONARQUBE);

    var server = model.createConnection();
    assertThat(server.getHostUrl()).isEqualTo("url");
    assertThat(server.isEnableProxy()).isTrue();
    assertThat(server.getLogin()).isNull();
    assertThat(server.getPassword()).isNull();
    assertThat(server.getToken()).isNull();
    assertThat(server.getOrganizationKey()).isEqualTo("org");
  }

  @Test
  void testExportSonarCloud() {
    var model = new ConnectionWizardModel();
    model.setName("name");
    model.setOrganizationKey("org");
    model.setToken("token");
    model.setPassword(new char[] {'p', 'a', 's', 's'});

    model.setServerType(ConnectionWizardModel.ServerType.SONARCLOUD);

    var server = model.createConnection();
    assertThat(server.getHostUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(server.getLogin()).isNull();
    assertThat(server.getPassword()).isNull();
    assertThat(server.getToken()).isNull();
    assertThat(server.getOrganizationKey()).isEqualTo("org");
  }

  @Test
  void testMigrationSonarCloud() {
    var server = ServerConnection.newBuilder()
      .setName("name")
      .setOrganizationKey("org")
      .setEnableProxy(true)
      .setHostUrl("https://www.sonarqube.com")
      .build();
    var model = new ConnectionWizardModel(server, credentialsServiceMock(true).getCredentials(server));

    server = model.createConnection();

    assertThat(server.isEnableProxy()).isTrue();
    assertThat(server.getHostUrl()).isEqualTo("https://sonarcloud.io");
  }

  private static CredentialsService credentialsServiceMock(boolean withToken) {
    var mock = mock(CredentialsService.class);
    when(mock.getCredentials(any()))
      .thenReturn(withToken
        ? Either.forLeft(new TokenDto("token"))
        : Either.forRight(new UsernamePasswordDto("login", "pass")));
    return mock;
  }
}
