/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.progress.ProgressManager;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.ServerConnectionCredentials;
import org.sonarlint.intellij.config.global.ServerConnectionWithAuth;
import org.sonarlint.intellij.config.global.SonarCloudConnection;
import org.sonarlint.intellij.config.global.SonarQubeConnection;
import org.sonarlint.intellij.core.SonarProduct;
import org.sonarlint.intellij.tasks.CheckNotificationsSupportedTask;
import org.sonarlint.intellij.tasks.GetOrganizationTask;
import org.sonarlint.intellij.tasks.GetOrganizationsTask;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.org.OrganizationDto;

import static org.sonarlint.intellij.common.util.SonarLintUtils.SONARCLOUD_URL;

public class WizardModel {
  private SonarProduct sonarProduct;
  private String serverUrl;
  private String token;
  private String login;
  private char[] password;
  private String name;
  private String organizationKey;
  private boolean notificationsDisabled = false;
  private boolean notificationsSupported = false;

  private List<OrganizationDto> organizationList = new ArrayList<>();

  public WizardModel() {

  }

  public WizardModel(String serverUrl) {
    this.serverUrl = serverUrl;
    this.sonarProduct = SonarProduct.fromUrl(serverUrl);
  }

  public WizardModel(ServerConnectionWithAuth connectionWithAuth) {
    var connection = connectionWithAuth.getConnection();
    this.sonarProduct = connection.getProduct();
    this.notificationsDisabled = connection.getNotificationsDisabled();
    this.serverUrl = connection.getHostUrl();
    if (sonarProduct == SonarProduct.SONARCLOUD) {
      this.organizationKey = ((SonarCloudConnection) connection).getOrganizationKey();
    }
    this.name = connection.getName();
    var credentials = connectionWithAuth.getCredentials();
    this.token = credentials.getToken();
    this.login = credentials.getLogin();
    var pass = credentials.getPassword();
    if (pass != null) {
      this.password = pass.toCharArray();
    }
  }

  @CheckForNull
  public SonarProduct getServerProduct() {
    return sonarProduct;
  }

  public boolean isSonarCloud() {
    return sonarProduct == SonarProduct.SONARCLOUD;
  }

  public boolean isNotificationsSupported() {
    return notificationsSupported;
  }

  public void setNotificationsSupported(boolean notificationsSupported) {
    this.notificationsSupported = notificationsSupported;
  }

  public void queryIfNotificationsSupported() throws Exception {
    final var partialConnection = createPartialConnection();
    var task = new CheckNotificationsSupportedTask(partialConnection);
    ProgressManager.getInstance().run(task);
    if (task.getException() != null) {
      throw task.getException();
    }
    setNotificationsSupported(task.notificationsSupported());
  }

  public void queryOrganizations() throws Exception {
    if (isSonarCloud()) {
      final var partialConnection = createPartialConnection();
      final var task = buildAndRunGetOrganizationsTask(partialConnection);
      setOrganizationList(task.organizations());
      final var presetOrganizationKey = getOrganizationKey();
      if (presetOrganizationKey != null) {
        addPresetOrganization(partialConnection, task, presetOrganizationKey);
      }
      if (getOrganizationKey() == null && task.organizations().size() == 1) {
        // if there is only one organization, we can preselect it
        setOrganizationKey(task.organizations().iterator().next().getKey());
      }
    }
  }

  private static GetOrganizationsTask buildAndRunGetOrganizationsTask(PartialConnection partialConnection) throws Exception {
    var task = new GetOrganizationsTask(partialConnection);
    ProgressManager.getInstance().run(task);
    if (task.getException() != null) {
      throw task.getException();
    }
    return task;
  }

  private void addPresetOrganization(PartialConnection partialConnection, GetOrganizationsTask task, String presetOrganizationKey) {
    // the previously configured organization might not be in the list. If that's the case, fetch it and add it to the list.
    var orgExists = task.organizations().stream().anyMatch(o -> o.getKey().equals(presetOrganizationKey));
    if (!orgExists) {
      var getOrganizationTask = new GetOrganizationTask(partialConnection, presetOrganizationKey);
      ProgressManager.getInstance().run(getOrganizationTask);
      final var fetchedOrganization = getOrganizationTask.organization();
      if (getOrganizationTask.getException() != null || fetchedOrganization == null) {
        // ignore and reset organization
        setOrganizationKey(null);
      } else {
        getOrganizationList().add(fetchedOrganization);
      }
    }
  }

  public boolean isNotificationsDisabled() {
    return notificationsDisabled;
  }

  public void setNotificationsDisabled(boolean notificationsDisabled) {
    this.notificationsDisabled = notificationsDisabled;
  }

  public List<OrganizationDto> getOrganizationList() {
    return organizationList;
  }

  public void setOrganizationList(List<OrganizationDto> organizationList) {
    this.organizationList = organizationList;
  }

  @CheckForNull
  public String getServerUrl() {
    return serverUrl;
  }

  public void setIsSonarCloud() {
    this.sonarProduct = SonarProduct.SONARCLOUD;
    this.serverUrl = SONARCLOUD_URL;
  }

  public void setIsSonarQube(String serverUrl) {
    this.sonarProduct = SonarProduct.SONARQUBE;
    this.serverUrl = serverUrl;
  }

  public void setToken(@Nullable String token) {
    this.token = token;
    this.login = null;
    this.password = null;
  }

  public void setLoginPassword(String login, char[] password) {
    this.login = login;
    this.password = password;
    this.token = null;
  }

  @CheckForNull
  public ServerConnectionCredentials getCredentials() {
    if (token != null) {
      return new ServerConnectionCredentials(null, null, token);
    }
    if (login != null && password != null) {
      return new ServerConnectionCredentials(login, String.valueOf(password), null);
    }
    return null;
  }

  @CheckForNull
  public String getName() {
    return name;
  }

  public WizardModel setName(String name) {
    this.name = name;
    return this;
  }

  @CheckForNull
  public String getOrganizationKey() {
    return organizationKey;
  }

  public WizardModel setOrganizationKey(@Nullable String organization) {
    this.organizationKey = organization;
    return this;
  }

  public PartialConnection createPartialConnection() {
    String pass = null;
    if (password != null) {
      pass = String.valueOf(password);
    }
    return new PartialConnection(serverUrl, sonarProduct, organizationKey, new ServerConnectionCredentials(login, pass, token));
  }

  public ServerConnectionWithAuth createConnectionWithAuth() {
    ServerConnection connection;
    String pass = null;
    if (sonarProduct == SonarProduct.SONARCLOUD) {
      connection = new SonarCloudConnection(name, organizationKey, notificationsDisabled);
    } else {
      if (password != null) {
        pass = String.valueOf(password);
      }
      connection = new SonarQubeConnection(name, serverUrl, notificationsDisabled);
    }
    return new ServerConnectionWithAuth(connection, new ServerConnectionCredentials(login, pass, token));
  }
}
