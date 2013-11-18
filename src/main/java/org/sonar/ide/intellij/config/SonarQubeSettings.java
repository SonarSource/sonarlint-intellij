/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.config;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.util.SonarQubeBundle;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

@State(name = "SonarQubeSettings", storages = {@Storage(id = "sonarqube", file = StoragePathMacros.APP_CONFIG + "/sonarqube.xml")})
public final class SonarQubeSettings implements PersistentStateComponent<SonarQubeSettings>, ExportableApplicationComponent {

  private static final Logger LOG = Logger.getInstance(SonarQubeSettings.class);
  private static String SERVER_ID_REGEXP = "[a-zA-Z0-9_\\-:\\.]+";
  private static Pattern SERVER_ID_PATTERN = Pattern.compile(SERVER_ID_REGEXP);

  private java.util.List<SonarQubeServer> servers = new java.util.ArrayList<SonarQubeServer>();

  public static SonarQubeSettings getInstance() {
    return com.intellij.openapi.application.ApplicationManager.getApplication().getComponent(SonarQubeSettings.class);
  }

  public List<SonarQubeServer> getServers() {
    return servers;
  }

  public void setServers(List<SonarQubeServer> servers) {
    this.servers = servers;
  }

  public SonarQubeSettings getState() {
    return this;
  }

  public void loadState(SonarQubeSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("sonarqube")};
  }

  @NotNull
  public String getPresentableName() {
    return SonarQubeBundle.message("sonarqube.settings");
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "SonarQubeSettings";
  }

  public static ValidationInfo validateNewServer(List<SonarQubeServer> otherServers, SonarQubeServer server, SonarQubeServerDialog dialog) {
    if (!SERVER_ID_PATTERN.matcher(server.getId()).matches()) {
      return new ValidationInfo("Invalid server ID: " + server.getId() + ". Should match " + SERVER_ID_REGEXP, dialog.getIdTextField());
    }
    for (SonarQubeServer other : otherServers) {
      if (other.getId().equals(server.getId())) {
        return new ValidationInfo(SonarQubeBundle.message("sonarqube.settings.server.duplicateId", server.getId()), dialog.getUrlTextField());
      }
    }
    return validateServer(server, dialog);
  }

  public static ValidationInfo validateServer(SonarQubeServer server, SonarQubeServerDialog dialog) {
    try {
      URL url = new URL(server.getUrl());
      if (url.getPort() != -1 && (url.getPort() < 0 || url.getPort() > 0xFFFF)) {
        return new ValidationInfo("Port out of range:" + url.getPort(), dialog.getUrlTextField());
      }
    } catch (MalformedURLException e) {
      return new ValidationInfo("Invalid URL: " + e.getMessage(), dialog.getUrlTextField());
    }
    return null;
  }

  public void initComponent() {
    // Nothing to do
  }

  public void disposeComponent() {
    // Nothing to do
  }

  public SonarQubeServer getServer(String serverId) {
    for (SonarQubeServer server : getServers()) {
      if (server.getId().equals(serverId)) {
        return server;
      }
    }
    return null;
  }
}
