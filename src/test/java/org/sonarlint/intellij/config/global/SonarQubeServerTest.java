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
