/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

@Immutable
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
public class SonarQubeServer {
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

  private SonarQubeServer() {
    // necessary for XML deserialization
  }

  private SonarQubeServer(Builder builder) {
    this.hostUrl = builder.hostUrl;
    this.token = builder.token;
    this.name = builder.name;
    this.login = builder.login;
    this.password = builder.password;
    this.enableProxy = builder.enableProxy;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SonarQubeServer)) {
      return false;
    }
    SonarQubeServer other = (SonarQubeServer) o;

    return Comparing.equal(getHostUrl(), other.getHostUrl()) &&
      Comparing.equal(getPassword(), other.getPassword()) &&
      Comparing.equal(getToken(), other.getToken()) &&
      Comparing.equal(getLogin(), other.getLogin()) &&
      Comparing.equal(getName(), other.getName()) &&
      Comparing.equal(enableProxy(), other.enableProxy());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getHostUrl(), getPassword(), getToken(), getLogin(), getName(), enableProxy);
  }

  @CheckForNull
  public String getLogin() {
    return login;
  }

  public String getHostUrl() {
    return hostUrl;
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

    private String name;
    private String login;
    private String password;
    private boolean enableProxy;

    private Builder() {
      // no args
    }

    public SonarQubeServer build() {
      return new SonarQubeServer(this);
    }

    public Builder setLogin(@Nullable String login) {
      this.login = login;
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
      this.token = PasswordUtil.encodePassword(token);
      return this;
    }

    public Builder setPassword(@Nullable String password) {
      this.password = PasswordUtil.encodePassword(password);
      return this;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

  }

}
