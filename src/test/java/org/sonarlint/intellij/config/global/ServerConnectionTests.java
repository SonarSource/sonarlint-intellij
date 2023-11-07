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
package org.sonarlint.intellij.config.global;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerConnectionTests {
  @Test
  void testRoundTrip() {
    var server = ServerConnection.newBuilder()
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

    assertThat(server).hasToString(server.getName());
    assertThat(server.isSonarCloud()).isFalse();
  }

  @Test
  void testSonarCloud() {
    var server1 = ServerConnection.newBuilder()
      .setHostUrl("https://sonarqube.com")
      .setPassword("pass")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();
    assertThat(server1.isSonarCloud()).isTrue();
  }

  @Test
  void testEqualsAndHash() {
    var server1 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setPassword("pass")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();

    var server2 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setPassword("pass")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();

    var server3 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setPassword("pass1")
      .setToken("token")
      .setName("name")
      .setLogin("login")
      .build();

    assertThat(server1)
      .isEqualTo(server2)
      .isNotEqualTo(server3)
      .isNotEqualTo(null)
      .hasSameHashCodeAs(server2);
  }

  @Test
  void testSetNullEncodedFields() {
    var server = ServerConnection.newBuilder()
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
  void testEncoded() {
    var builder = ServerConnection.newBuilder()
      .setPassword("pass")
      .setToken("token");

    ServerConnection connection = builder.build();
    assertThat(connection.getPassword()).isEqualTo("pass");
    assertThat(connection.getToken()).isEqualTo("token");
  }
}
