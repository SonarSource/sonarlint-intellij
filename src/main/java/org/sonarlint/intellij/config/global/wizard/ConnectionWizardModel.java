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

import com.intellij.openapi.progress.ProgressManager;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.tasks.GetOrganizationTask;
import org.sonarlint.intellij.tasks.GetOrganizationsTask;
import org.sonarlint.intellij.util.RegionUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.OrganizationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

public class ConnectionWizardModel {
  private ServerType serverType;
  private String serverUrl;
  private String token;
  private String login;
  private char[] password;
  private String name;
  private String organizationKey;
  private boolean proxyEnabled;
  private boolean notificationsDisabled = false;
  private SonarCloudRegion region = SonarCloudRegion.EU;

  private List<OrganizationDto> organizationList = new ArrayList<>();

  public enum ServerType {
    SONARCLOUD,
    SONARQUBE
  }

  public ConnectionWizardModel() {

  }

  public ConnectionWizardModel(ServerConnection prefilledConnection) {
    if (SonarLintUtils.isSonarCloudAlias(prefilledConnection.getHostUrl())) {
      serverType = ServerType.SONARCLOUD;
      if (prefilledConnection.getRegion() != null) {
        region = SonarCloudRegion.valueOf(prefilledConnection.getRegion());
      }
    } else {
      serverType = ServerType.SONARQUBE;
      serverUrl = prefilledConnection.getHostUrl();
    }
    this.proxyEnabled = prefilledConnection.isEnableProxy();
    this.organizationKey = prefilledConnection.getOrganizationKey();
    this.notificationsDisabled = prefilledConnection.isDisableNotifications();
    this.name = prefilledConnection.getName();
  }

  public ConnectionWizardModel(ServerConnection connectionToEdit, Either<TokenDto, UsernamePasswordDto> credentials) {
    this(connectionToEdit);

    if (credentials.isLeft()) {
      this.token = credentials.getLeft().getToken();
    } else {
      this.login = credentials.getRight().getUsername();
      var pass = credentials.getRight().getPassword();
      if (pass != null) {
        this.password = pass.toCharArray();
      }
    }
  }

  @CheckForNull
  public ServerType getServerType() {
    return serverType;
  }

  public ConnectionWizardModel setServerType(ServerType serverType) {
    this.serverType = serverType;
    return this;
  }

  public boolean isSonarCloud() {
    return ServerType.SONARCLOUD.equals(serverType);
  }

  public void queryOrganizations() throws Exception {
    if (isSonarCloud()) {
      final ServerConnection partialConnection = createConnectionWithoutOrganization();
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

  private static GetOrganizationsTask buildAndRunGetOrganizationsTask(ServerConnection partialConnection) throws Exception {
    var task = new GetOrganizationsTask(partialConnection);
    ProgressManager.getInstance().run(task);
    if (task.getException() != null) {
      throw task.getException();
    }
    return task;
  }

  private void addPresetOrganization(ServerConnection partialConnection, GetOrganizationsTask task, String presetOrganizationKey) {
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

  public ConnectionWizardModel setNotificationsDisabled(boolean notificationsDisabled) {
    this.notificationsDisabled = notificationsDisabled;
    return this;
  }

  public boolean isProxyEnabled() {
    return proxyEnabled;
  }

  public ConnectionWizardModel setProxyEnabled(boolean proxyEnabled) {
    this.proxyEnabled = proxyEnabled;
    return this;
  }

  public List<OrganizationDto> getOrganizationList() {
    return organizationList;
  }

  public ConnectionWizardModel setOrganizationList(List<OrganizationDto> organizationList) {
    this.organizationList = organizationList;
    return this;
  }

  @CheckForNull
  public String getServerUrl() {
    return serverUrl;
  }

  public ConnectionWizardModel setServerUrl(@Nullable String serverUrl) {
    this.serverUrl = serverUrl;
    return this;
  }

  @CheckForNull
  public String getToken() {
    return token;
  }

  public ConnectionWizardModel setToken(@Nullable String token) {
    this.token = token;
    return this;
  }

  @CheckForNull
  public String getLogin() {
    return login;
  }

  public ConnectionWizardModel setLogin(@Nullable String login) {
    this.login = login;
    return this;
  }

  @CheckForNull
  public char[] getPassword() {
    return password;
  }

  public ConnectionWizardModel setPassword(@Nullable char[] password) {
    this.password = password;
    return this;
  }

  @CheckForNull
  public String getName() {
    return name;
  }

  public ConnectionWizardModel setName(String name) {
    this.name = name;
    return this;
  }

  @CheckForNull
  public String getOrganizationKey() {
    return organizationKey;
  }

  public ConnectionWizardModel setOrganizationKey(@Nullable String organization) {
    this.organizationKey = organization;
    return this;
  }

  public SonarCloudRegion getRegion() {
    return region;
  }

  public ConnectionWizardModel setRegion(SonarCloudRegion region) {
    this.region = region;
    return this;
  }

  public ServerConnection createConnectionWithoutOrganization() {
    return createConnection(null);
  }

  public ServerConnection createConnection() {
    return createConnection(organizationKey);
  }

  private ServerConnection.Builder createUnauthenticatedConnection(@Nullable String organizationKey) {
    var builder = ServerConnection.newBuilder()
      .setOrganizationKey(organizationKey)
      .setEnableProxy(proxyEnabled)
      .setName(name);

    if (serverType == ServerType.SONARCLOUD) {
      builder.setHostUrl(RegionUtils.getUrlByRegion(region));
      builder.setRegion(region.name());
    } else {
      builder.setHostUrl(serverUrl);
    }
    builder.setDisableNotifications(notificationsDisabled);
    return builder;
  }

  private ServerConnection createConnection(@Nullable String organizationKey) {
    var builder = createUnauthenticatedConnection(organizationKey);

    return builder.build();
  }
}
