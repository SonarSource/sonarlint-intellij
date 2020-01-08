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
package org.sonarlint.intellij.config.global.wizard;

import org.junit.Test;
import org.sonarlint.intellij.config.global.SonarQubeServer;

import static org.assertj.core.api.Assertions.assertThat;

public class WizardModelTest {
  @Test
  public void testCreateFromConfig() {
    SonarQubeServer server = SonarQubeServer.newBuilder()
      .setName("name")
      .setToken("token")
      .setOrganizationKey("org")
      .setEnableProxy(true)
      .setHostUrl("url")
      .build();

    WizardModel model = new WizardModel(server);
    assertThat(model.getLogin()).isNull();
    assertThat(model.getPassword()).isNull();
    assertThat(model.getToken()).isEqualTo("token");
    assertThat(model.getOrganizationKey()).isEqualTo("org");
    assertThat(model.getOrganizationList()).isNull();
    assertThat(model.getName()).isEqualTo("name");
    assertThat(model.getServerUrl()).isEqualTo("url");
  }

  @Test
  public void testExportToConfig() {
    WizardModel model = new WizardModel();
    model.setName("name");
    model.setOrganizationKey("org");
    model.setServerUrl("url");
    model.setLogin("login");
    model.setProxyEnabled(true);
    model.setPassword(new char[] {'p', 'a', 's', 's'});

    model.setServerType(WizardModel.ServerType.SONARQUBE);

    SonarQubeServer server = model.createServer();
    assertThat(server.getHostUrl()).isEqualTo("url");
    assertThat(server.enableProxy()).isTrue();
    assertThat(server.getLogin()).isEqualTo("login");
    assertThat(server.getPassword()).isEqualTo("pass");
    assertThat(server.getToken()).isNull();
    assertThat(server.getOrganizationKey()).isEqualTo("org");
  }

  @Test
  public void testExportSonarCloud() {
    WizardModel model = new WizardModel();
    model.setName("name");
    model.setOrganizationKey("org");
    model.setToken("token");
    model.setPassword(new char[] {'p', 'a', 's', 's'});

    model.setServerType(WizardModel.ServerType.SONARCLOUD);

    SonarQubeServer server = model.createServer();
    assertThat(server.getHostUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(server.getLogin()).isNull();
    assertThat(server.getPassword()).isNull();
    assertThat(server.getToken()).isEqualTo("token");
    assertThat(server.getOrganizationKey()).isEqualTo("org");
  }

  @Test
  public void testMigrationSonarCloud() {
    SonarQubeServer server = SonarQubeServer.newBuilder()
      .setName("name")
      .setToken("token")
      .setOrganizationKey("org")
      .setEnableProxy(true)
      .setHostUrl("https://www.sonarqube.com")
      .build();
    WizardModel model = new WizardModel(server);

    server = model.createServer();
    assertThat(server.enableProxy()).isTrue();
    assertThat(server.getHostUrl()).isEqualTo("https://sonarcloud.io");
  }
}
