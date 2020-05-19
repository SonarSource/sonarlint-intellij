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
package org.sonarlint.intellij.config.global;

import com.google.common.base.Objects;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonarlint.intellij.util.PasswordSafeUtil;
import org.sonarlint.intellij.util.SonarLintUtils;

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
public class SonarQubeServer {

  static final String PASSWORD_KEY = "Password";
  static final String TOKEN_KEY = "Token";

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
  private boolean enableNotifications;

  private SonarQubeServer() {
    // necessary for XML deserialization
  }

  private SonarQubeServer(Builder builder) {
    this.hostUrl = builder.hostUrl;
    this.name = builder.name;
    this.login = builder.login;
    if (builder.persistSensitiveData) {
      PasswordSafeUtil.replaceServerAttributeInPasswordSafe(name, TOKEN_KEY, builder.token);
      PasswordSafeUtil.replaceServerAttributeInPasswordSafe(name, PASSWORD_KEY, builder.password);
    } else {
      PasswordSafeUtil.putServerAttributeInMemorySafe(name, TOKEN_KEY, builder.token);
      PasswordSafeUtil.putServerAttributeInMemorySafe(name, PASSWORD_KEY, builder.password);
    }
    this.enableProxy = builder.enableProxy;
    this.organizationKey = builder.organizationKey;
    this.enableNotifications = builder.enableNotifications;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SonarQubeServer)) {
      return false;
    }
    SonarQubeServer other = (SonarQubeServer) o;

    return Comparing.equal(getHostUrl(), other.getHostUrl()) &&
      Comparing.equal(password, other.password) &&
      Comparing.equal(token, other.token) &&
      Comparing.equal(getLogin(), other.getLogin()) &&
      Comparing.equal(getName(), other.getName()) &&
      Comparing.equal(getOrganizationKey(), other.getOrganizationKey()) &&
      Comparing.equal(enableProxy(), other.enableProxy()) &&
      Comparing.equal(enableNotifications(), other.enableNotifications());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getHostUrl(), password, token, getLogin(), getOrganizationKey(), getName(), enableProxy, enableNotifications);
  }

  public boolean enableNotifications() {
    return enableNotifications;
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

  public String getSecureToken() {
    return PasswordSafeUtil.getServerAttributeFromPasswordSafe(name, TOKEN_KEY);
  }

  public String getSecurePassword() {
    return PasswordSafeUtil.getServerAttributeFromPasswordSafe(name, PASSWORD_KEY);
  }

  public boolean isSonarCloud() {
    return SonarLintUtils.isSonarCloudAlias(hostUrl);
  }

  public boolean enableProxy() {
    return enableProxy;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  void migrateOldStyleCredentials() {
    if (password != null) {
      PasswordSafeUtil.replaceServerAttributeInPasswordSafe(name, PASSWORD_KEY, PasswordUtil.decodePassword(password));
      password = null;
    }
    if (token != null) {
      PasswordSafeUtil.replaceServerAttributeInPasswordSafe(name, TOKEN_KEY, PasswordUtil.decodePassword(token));
      token = null;
    }
  }

  public static Builder newPersistentBuilder() {
    return new Builder(true);
  }

  public static Builder newMemoryBuilder() {
    return new Builder(false);
  }

  public static class Builder {
    private String hostUrl;
    private String token;
    private String organizationKey;
    private String name;
    private String login;
    private String password;
    private boolean enableProxy;
    private boolean enableNotifications;
    private final boolean persistSensitiveData;

    private Builder(boolean persistSensitiveData) {
      this.persistSensitiveData = persistSensitiveData;
    }

    public SonarQubeServer build() {
      return new SonarQubeServer(this);
    }

    public Builder setLogin(@Nullable String login) {
      this.login = login;
      return this;
    }

    public Builder setEnableNotifications(boolean enableNotifications) {
      this.enableNotifications = enableNotifications;
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
      this.token = token;
      return this;
    }

    public Builder setPassword(@Nullable String password) {
      this.password = password;
      return this;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

  }

}
