/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.config.global;

import com.google.common.base.Objects;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.swing.Icon;

import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.http.ApacheHttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

import static icons.SonarLintIcons.ICON_SONARCLOUD_16;
import static icons.SonarLintIcons.ICON_SONARQUBE_16;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isBlank;

/**
 * This class is serialized in XML when SonarLintGlobalSettings is saved by IntelliJ.
 * By default, it will serialize data when there are public setters and getters for a field or when the field is public.
 * As this class is immutable, there are no setters and the fields are private, so nothing will be serialized by default.
 * Therefore, we must add the appropriate annotations for the fields we want to annotate.
 * Note that we use both {@link OptionTag} and {@link Tag} (which will result in 2 different ways of serializing the fields) to remain
 * backward-compatible with existing serialized configurations.
 *
 * @see com.intellij.util.xmlb.annotations.Tag
 * @see com.intellij.util.xmlb.annotations.OptionTag
 */
@Immutable
// Don't change annotation, used for backward compatibility
@Tag("SonarQubeServer")
public class ServerConnection {
  @OptionTag
  private String hostUrl;
  @Tag
  private String token;
  @OptionTag
  private String name;
  @OptionTag
  private String login;
  @Tag
  private String password;
  @OptionTag
  private boolean enableProxy;
  @Tag
  private String organizationKey;
  @Tag
  private boolean disableNotifications;

  private ServerConnection() {
    // necessary for XML deserialization
  }

  private ServerConnection(Builder builder) {
    this.hostUrl = builder.hostUrl;
    this.token = builder.token;
    this.name = builder.name;
    this.login = builder.login;
    this.password = builder.password;
    this.enableProxy = builder.enableProxy;
    this.organizationKey = builder.organizationKey;
    this.disableNotifications = builder.disableNotifications;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ServerConnection)) {
      return false;
    }
    var other = (ServerConnection) o;

    return Comparing.equal(getHostUrl(), other.getHostUrl()) &&
      Comparing.equal(getPassword(), other.getPassword()) &&
      Comparing.equal(getToken(), other.getToken()) &&
      Comparing.equal(getLogin(), other.getLogin()) &&
      Comparing.equal(getName(), other.getName()) &&
      Comparing.equal(getOrganizationKey(), other.getOrganizationKey()) &&
      Comparing.equal(enableProxy(), other.enableProxy()) &&
      Comparing.equal(isDisableNotifications(), other.isDisableNotifications());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getHostUrl(), getPassword(), getToken(), getLogin(), getOrganizationKey(), getName(), enableProxy, disableNotifications);
  }

  public boolean isDisableNotifications() {
    return disableNotifications;
  }

  @CheckForNull
  public String getLogin() {
    return login;
  }

  public String getHostUrl() {
    return hostUrl;
  }

  @CheckForNull
  public String getOrganizationKey() {
    return organizationKey;
  }

  @CheckForNull
  public String getToken() {
    if (token == null) {
      return null;
    }
    try {
      return PasswordUtil.decodePassword(token);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public boolean isSonarCloud() {
    return SonarLintUtils.isSonarCloudAlias(hostUrl);
  }

  public String getProductName() {
    return isSonarCloud() ? "SonarCloud" : "SonarQube";
  }

  public Icon getProductIcon() {
    return isSonarCloud() ? ICON_SONARCLOUD_16 : ICON_SONARQUBE_16;
  }

  public boolean enableProxy() {
    return enableProxy;
  }

  @CheckForNull
  public String getPassword() {
    if (password == null) {
      return null;
    }
    try {
      return PasswordUtil.decodePassword(password);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public String getName() {
    return name;
  }

  public EndpointParams getEndpointParams() {
    return new EndpointParams(getHostUrl(), isSonarCloud(), getOrganizationKey());
  }

  public HttpClient getHttpClient() {
    var userToken = getToken();
    return ApacheHttpClient.getDefault().withCredentials(isBlank(userToken) ? getLogin() : userToken, getPassword());
  }

  public ServerApi api() {
    return new ServerApi(getEndpointParams(), getHttpClient());
  }

  @Override
  public String toString() {
    return name;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String hostUrl;
    private String token;
    private String organizationKey;
    private String name;
    private String login;
    private String password;
    private boolean enableProxy;
    private boolean disableNotifications;

    private Builder() {
      // no args
    }

    public ServerConnection build() {
      return new ServerConnection(this);
    }

    public Builder setLogin(@Nullable String login) {
      this.login = login;
      return this;
    }

    public Builder setDisableNotifications(boolean disableNotifications) {
      this.disableNotifications = disableNotifications;
      return this;
    }

    public Builder setOrganizationKey(@Nullable String organizationKey) {
      this.organizationKey = organizationKey;
      return this;
    }

    public Builder setHostUrl(String hostUrl) {
      this.hostUrl = hostUrl;
      return this;
    }

    public Builder setEnableProxy(boolean enableProxy) {
      this.enableProxy = enableProxy;
      return this;
    }

    public Builder setToken(@Nullable String token) {
      if (token == null) {
        this.token = null;
      } else {
        this.token = PasswordUtil.encodePassword(token);
      }
      return this;
    }

    public Builder setPassword(@Nullable String password) {
      if (password == null) {
        this.password = null;
      } else {
        this.password = PasswordUtil.encodePassword(password);
      }
      return this;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

  }

}
