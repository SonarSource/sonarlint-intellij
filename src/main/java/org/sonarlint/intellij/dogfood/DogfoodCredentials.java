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
package org.sonarlint.intellij.dogfood;

import com.intellij.util.xmlb.annotations.Tag;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@Deprecated(since = "11.1", forRemoval = true)
public class DogfoodCredentials {

  @Tag
  private String username;
  @Tag
  private String pass;

  private DogfoodCredentials(Builder builder) {
    this.username = builder.username;
    this.pass = builder.pass;
  }

  public DogfoodCredentials() {
    // necessary for XML deserialization
  }

  public String getUsername() {
    return username;
  }

  public String getPass() {
    return pass;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {

    private String username;
    private String pass;

    private Builder() {
      // no args
    }

    public DogfoodCredentials build() {
      return new DogfoodCredentials(this);
    }

    public Builder setUsername(@Nullable String username) {
      this.username = username;
      return this;
    }

    public Builder setPassword(@Nullable String pass) {
      this.pass = pass;
      return this;
    }

  }

}
