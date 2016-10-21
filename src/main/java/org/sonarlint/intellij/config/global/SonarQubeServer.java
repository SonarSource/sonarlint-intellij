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
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public class SonarQubeServer {
  private final String hostUrl;
  private final String token;

  private final String name;
  private final String login;
  private final String password;
  private final boolean enableProxy;

  private SonarQubeServer(Builder builder) {
    this.hostUrl = builder.getHostUrl();
    this.token = builder.getToken();
    this.name = builder.getName();
    this.login = builder.getLogin();
    this.password = builder.getPassword();
    this.enableProxy = builder.enableProxy();
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

  @Transient
  public String getToken() {
    return token;
  }

  @Tag("token")
  public String getEncodedToken() {
    return PasswordUtil.encodePassword(getToken());
  }

  public boolean enableProxy() {
    return enableProxy;
  }

  @CheckForNull
  @Transient
  public String getPassword() {
    return password;
  }

  @Tag("password")
  public String getEncodedPassword() {
    return PasswordUtil.encodePassword(getPassword());
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

    @CheckForNull
    public String getLogin() {
      return login;
    }

    public Builder setLogin(@Nullable String login) {
      this.login = login;
      return this;
    }

    public String getHostUrl() {
      return hostUrl;
    }

    public Builder setHostUrl(String hostUrl) {
      this.hostUrl = hostUrl;
      return this;
    }

    @Transient
    public String getToken() {
      return token;
    }

    @Tag("token")
    public String getEncodedToken() {
      return PasswordUtil.encodePassword(getToken());
    }

    public Builder setEncodedToken(String token) {
      try {
        setToken(PasswordUtil.decodePassword(token));
      } catch (NumberFormatException e) {
        // do nothing
      }
      return this;
    }

    public boolean enableProxy() {
      return enableProxy;
    }

    public Builder setEnableProxy(boolean enableProxy) {
      this.enableProxy = enableProxy;
      return this;
    }

    @CheckForNull
    @Transient
    public String getPassword() {
      return password;
    }

    @Tag("password")
    public String getEncodedPassword() {
      return PasswordUtil.encodePassword(getPassword());
    }

    public Builder setEncodedPassword(String password) {
      try {
        setPassword(PasswordUtil.decodePassword(password));
      } catch (NumberFormatException e) {
        // do nothing
      }
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

    public String getName() {
      return name;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

  }

}
