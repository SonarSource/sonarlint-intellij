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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarQubeServerTest {
  @Test
  public void testRoundTrip() {
    SonarQubeServer server = SonarQubeServer.newBuilder()
      .setHostUrl("host")
      .setPassword("pass")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();

    assertThat(server.getName()).isEqualTo("name");
    assertThat(server.getToken()).isEqualTo("token");
    assertThat(server.getLogin()).isEqualTo("login");
    assertThat(server.getPassword()).isEqualTo("pass");
    assertThat(server.getHostUrl()).isEqualTo("host");

    assertThat(server.toString()).isEqualTo(server.getName());
    assertThat(server.isSonarCloud()).isFalse();
  }

  @Test
  public void testSonarCloud() {
    SonarQubeServer server1 = SonarQubeServer.newBuilder()
      .setHostUrl("https://sonarqube.com")
      .setPassword("pass")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();
    assertThat(server1.isSonarCloud()).isTrue();
  }

  @Test
  public void testEqualsAndHash() {
    SonarQubeServer server1 = SonarQubeServer.newBuilder()
      .setHostUrl("host")
      .setPassword("pass")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();

    SonarQubeServer server2 = SonarQubeServer.newBuilder()
      .setHostUrl("host")
      .setPassword("pass")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();

    SonarQubeServer server3 = SonarQubeServer.newBuilder()
      .setHostUrl("host")
      .setPassword("pass1")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();

    assertThat(server1.equals(server2)).isTrue();
    assertThat(server1.equals(server3)).isFalse();
    assertThat(server1.equals(null)).isFalse();

    assertThat(server1.hashCode()).isEqualTo(server2.hashCode());
  }

  @Test
  public void testSetNullEncodedFields() {
    SonarQubeServer server = SonarQubeServer.newBuilder()
      .setHostUrl("host")
      .setPassword(null)
      .setToken(null)
      .setName("name")
      .setLogin("login")
      .build();

    assertThat(server.getToken()).isNull();
    assertThat(server.getPassword()).isNull();
  }

  @Test
  public void testEncoded() {
    SonarQubeServer.Builder builder = SonarQubeServer.newBuilder()
      .setPassword("pass")
      .setToken("token");

    assertThat(builder.build().getPassword()).isEqualTo("pass");
    assertThat(builder.build().getToken()).isEqualTo("token");
  }
}
