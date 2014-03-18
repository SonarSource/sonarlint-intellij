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
package org.sonar.ide.intellij.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.annotations.Transient;
import org.apache.commons.lang.StringUtils;
import org.sonar.ide.intellij.wsclient.ISonarWSClientFacade;
import org.sonar.ide.intellij.wsclient.SonarWSClientException;
import org.sonar.ide.intellij.wsclient.WSClientFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;

public class SonarQubeServer implements ISonarServer {

  private static final Logger LOG = Logger.getInstance(SonarQubeServer.class);

  private String id;
  private String url;
  private String username;
  @Transient
  private String password;
  @Transient
  private String version;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = StringUtils.removeEnd(url, "/");
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(@Nullable String username) {
    this.username = username;
  }

  @Transient
  public String getPassword() {
    return password;
  }

  public void setPassword(@Nullable String password) {
    this.password = password;
  }

  @Override
  public boolean hasCredentials() {
    return StringUtils.isNotBlank(getPassword()) && StringUtils.isNotBlank(getUsername());
  }

  @Transient
  public String getVersion() {
    if (version == null) {
      ISonarWSClientFacade sonarClient = WSClientFactory.getInstance().getSonarClient(this);
      version =  sonarClient.getServerVersion();
    }
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }
}
