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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.util.PasswordUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonarlint.intellij.common.util.SonarLintUtils;

/**
 * This class is serialized in XML when SonarLintGlobalSettings is saved by IntelliJ.
 * By default, it will serialize data when there are public setters and getters for a field or when the field is public.
 * As this class is immutable, there are no setters and the fields are private, so nothing will be serialized by default.
 * Therefore, we must add the appropriate annotations for the fields we want to annotate.
 * Note that we use both {@link OptionTag} and {@link Tag} (which will result in 2 different ways of serializing the fields) to remain
 * backward-compatible with existing serialized configurations.
 *
 * @see Tag
 * @see OptionTag
 */
@Immutable
// Don't change annotation, used for backward compatibility
@Tag("SonarQubeServer")
public class ServerConnectionSettings {
  @OptionTag
  private String hostUrl;
  // credentials are migrated to secure storage
  @Tag
  @Deprecated(since = "10.0")
  private String token;
  @OptionTag
  private String name;
  @OptionTag
  @Deprecated(since = "10.0")
  private String login;
  @Tag
  @Deprecated(since = "10.0")
  private String password;
  @Tag
  private String organizationKey;
  @Tag
  private boolean disableNotifications;

  private ServerConnectionSettings() {
    // necessary for XML deserialization
  }

  private ServerConnectionSettings(Builder builder) {
    this.hostUrl = builder.hostUrl;
    this.name = builder.name;
    this.organizationKey = builder.organizationKey;
    this.disableNotifications = builder.disableNotifications;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ServerConnectionSettings other)) {
      return false;
    }

    return Objects.equals(getHostUrl(), other.getHostUrl()) &&
      Objects.equals(getPassword(), other.getPassword()) &&
      Objects.equals(getToken(), other.getToken()) &&
      Objects.equals(getLogin(), other.getLogin()) &&
      Objects.equals(getName(), other.getName()) &&
      Objects.equals(getOrganizationKey(), other.getOrganizationKey()) &&
      Objects.equals(isDisableNotifications(), other.isDisableNotifications());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHostUrl(), getPassword(), getToken(), getLogin(), getOrganizationKey(), getName(), disableNotifications);
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

  public void clearCredentials() {
    this.login = null;
    this.password = null;
    this.token = null;
  }

  public boolean isSonarCloud() {
    return SonarLintUtils.isSonarCloudAlias(hostUrl);
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

  @Override
  public String toString() {
    return name;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String hostUrl;
    private String organizationKey;
    private String name;
    private boolean disableNotifications;

    private Builder() {
      // no args
    }

    public ServerConnectionSettings build() {
      return new ServerConnectionSettings(this);
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

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

  }

}
