/**
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

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarQubeServerTest {
  private SonarQubeServer server;

  @Before
  public void setUp() {
    server = new SonarQubeServer();
  }

  @Test
  public void testRoundTrip() {
    server.setHostUrl("host");
    server.setPassword("pass");
    server.setStorageId("id");
    server.setToken("token");
    server.setName("name");
    server.setLogin("login");

    assertThat(server.getName()).isEqualTo("name");
    assertThat(server.getToken()).isEqualTo("token");
    assertThat(server.getLogin()).isEqualTo("login");
    assertThat(server.getPassword()).isEqualTo("pass");
    assertThat(server.getStorageId()).isEqualTo("id");
    assertThat(server.getHostUrl()).isEqualTo("host");
  }

  @Test
  public void testEncoded() {
    server.setPassword("pass");
    server.setToken("token");

    String encodedPass = server.getEncodedPassword();
    String encodedToken = server.getEncodedToken();
    server.setEncodedPassword(encodedPass);
    server.setEncodedToken(encodedToken);

    assertThat(server.getEncodedPassword()).isEqualTo(encodedPass);
    assertThat(server.getEncodedToken()).isEqualTo(encodedToken);

    assertThat(server.getPassword()).isEqualTo("pass");
    assertThat(server.getToken()).isEqualTo("token");
  }
}
