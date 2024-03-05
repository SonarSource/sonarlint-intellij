/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.util.PasswordUtil;

public class DogfoodCredentials {

  private String username;
  private String pass;

  public DogfoodCredentials(String username, String pass) {
    this.username = username;
    if (pass != null) {
      this.pass = PasswordUtil.encodePassword(pass);
    }
  }

  public DogfoodCredentials() {
    // necessary for XML deserialization
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getDecodedPass() {
    return PasswordUtil.decodePassword(pass);
  }

  public String getPass() {
    return pass;
  }

  public void setPass(String pass) {
    this.pass = PasswordUtil.encodePassword(pass);
  }

}
