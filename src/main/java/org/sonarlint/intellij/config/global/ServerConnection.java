/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.openapi.util.PasswordUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.swing.Icon;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.server.ServerLinks;
import org.sonarlint.intellij.core.server.SonarCloudLinks;
import org.sonarlint.intellij.core.server.SonarQubeLinks;
import org.sonarsource.sonarlint.core.SonarCloudRegion;

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
  private String region;
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
    this.region = builder.region;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ServerConnection other)) {
      return false;
    }

    return Objects.equals(getHostUrl(), other.getHostUrl()) &&
      Objects.equals(getPassword(), other.getPassword()) &&
      Objects.equals(getToken(), other.getToken()) &&
      Objects.equals(getLogin(), other.getLogin()) &&
      Objects.equals(getName(), other.getName()) &&
      Objects.equals(getOrganizationKey(), other.getOrganizationKey()) &&
      Objects.equals(enableProxy(), other.enableProxy()) &&
      Objects.equals(isDisableNotifications(), other.isDisableNotifications());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHostUrl(), getPassword(), getToken(), getLogin(), getOrganizationKey(), getName(), enableProxy, disableNotifications);
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

  public String getRegion() {
    return region;
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

  public boolean isSonarQube() {
    return !isSonarCloud();
  }

  public String getProductName() {
    return isSonarCloud() ? "SonarQube Cloud" : "SonarQube Server";
  }

  public Icon getProductIcon() {
    return isSonarCloud() ? SonarLintIcons.ICON_SONARQUBE_CLOUD_16 : SonarLintIcons.ICON_SONARQUBE_SERVER_16;
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

  public boolean hasSameCredentials(ServerConnection otherConnection) {
    if (token != null) {
      return Objects.equals(token, otherConnection.token);
    }
    return Objects.equals(password, otherConnection.password) && Objects.equals(login, otherConnection.login);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public ServerLinks links() {
    return isSonarCloud() ? SonarCloudLinks.INSTANCE : new SonarQubeLinks(hostUrl);
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
    private String region;

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

    public Builder setRegion(String region) {
      this.region = region;
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
