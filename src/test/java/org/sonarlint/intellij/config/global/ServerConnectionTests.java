/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
      .setName("name")
      .build();

    assertThat(server.getName()).isEqualTo("name");
    assertThat(server.getHostUrl()).isEqualTo("host");

    assertThat(server).hasToString(server.getName());
    assertThat(server.isSonarCloud()).isFalse();
  }

  @Test
  void testSonarCloud() {
    var server1 = ServerConnection.newBuilder()
      .setHostUrl("https://sonarqube.com")
      .setName("name")
      .build();
    assertThat(server1.isSonarCloud()).isTrue();
  }

  @Test
  void testEqualsAndHash() {
    var server1 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setName("name")
      .build();

    var server2 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setName("name")
      .build();

    var server3 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setName("name1")
      .build();

    assertThat(server1)
      .isEqualTo(server2)
      .isNotEqualTo(server3)
      .isNotEqualTo(null)
      .hasSameHashCodeAs(server2);
  }
}
