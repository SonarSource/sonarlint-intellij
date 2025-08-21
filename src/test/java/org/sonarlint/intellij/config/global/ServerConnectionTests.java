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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarlint.intellij.SonarLintTestUtils.clearCredentialsForConnection;

class ServerConnectionTests {

  public static final String CONNECTION_NAME = "name";
  public static final String CONNECTION_NAME_2 = "name2";

  @BeforeEach
  void clearCredentials() {
    clearCredentialsForConnection(CONNECTION_NAME);
    clearCredentialsForConnection(CONNECTION_NAME_2);
  }

  @Test
  void should_test_round_trip() {
    var server = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setPassword("pass")
      .setToken("token")
      .setName(CONNECTION_NAME)
      .setLogin("login")
      .build();

    assertThat(server.getName()).isEqualTo(CONNECTION_NAME);
    assertThat(server.getToken()).isEqualTo("token");
    assertThat(server.getLogin()).isEqualTo("login");
    assertThat(server.getPassword()).isEqualTo("pass");
    assertThat(server.getHostUrl()).isEqualTo("host");
    assertThat(server).hasToString(server.getName());
    assertThat(server.isSonarCloud()).isFalse();
  }

  @Test
  void test_equals_and_hash() {
    var server1 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setPassword("pass")
      .setToken("token")
      .setName(CONNECTION_NAME)
      .setLogin("login")
      .build();

    var server2 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setPassword("pass")
      .setToken("token")
      .setName(CONNECTION_NAME)
      .setLogin("login")
      .build();

    var server3 = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setPassword("pass1")
      .setToken("token")
      .setName(CONNECTION_NAME)
      .setLogin("login")
      .build();

    assertThat(server1)
      .isEqualTo(server2)
      .isNotEqualTo(server3)
      .isNotEqualTo(null)
      .hasSameHashCodeAs(server2);
  }

  @Test
  void should_set_null_credentials() {
    var server = ServerConnection.newBuilder()
      .setHostUrl("host")
      .setPassword(null)
      .setToken(null)
      .setName(CONNECTION_NAME)
      .setLogin("login")
      .build();

    assertThat(server.getToken()).isNull();
    assertThat(server.getPassword()).isNull();
  }

  @Test
  void should_correctly_decode_password() {
    var builder = ServerConnection.newBuilder()
      .setPassword("pass")
      .setToken("token");

    var connection = builder.build();
    assertThat(connection.getPassword()).isEqualTo("pass");
    assertThat(connection.getToken()).isEqualTo("token");
  }

  @Test
  void should_handle_legacy_credentials() throws Exception {
    // Create a server connection with legacy encoded credentials (simulating XML deserialization)
    var connection = createConnectionWithLegacyCredentials();
    
    // Should be able to decode the legacy credentials
    assertThat(connection.getToken()).isEqualTo("mytoken");
    assertThat(connection.getPassword()).isEqualTo("mypassword");
    assertThat(connection.getLogin()).isEqualTo("mylogin");
    assertThat(connection.getName()).isEqualTo(CONNECTION_NAME);
  }

  @Test
  @DisplayName("Should handle corrupted legacy credentials gracefully")
  void should_handle_corrupted_legacy_credentials() throws Exception {
    var connection = createConnectionWithCorruptedCredentials();
    
    assertThat(connection.getToken()).isNull();
    assertThat(connection.getPassword()).isNull();
  }

  @Test
  void should_handle_connection_without_credentials() {
    var connection = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME_2)
      .setHostUrl("https://sonarqube.example.com")
      .build();

    assertThat(connection.getToken()).isNull();
    assertThat(connection.getPassword()).isNull();
    assertThat(connection.getLogin()).isNull();
    assertThat(connection.getName()).isEqualTo(CONNECTION_NAME_2);
    assertThat(connection.getHostUrl()).isEqualTo("https://sonarqube.example.com");
  }

  @Test
  void should_correctly_identify_server_type() {
    var sonarCloudConnection = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME)
      .setHostUrl("https://sonarcloud.io")
      .build();

    var sonarQubeConnection = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME_2)
      .setHostUrl("https://sonarqube.example.com")
      .build();

    assertThat(sonarCloudConnection.isSonarCloud()).isTrue();
    assertThat(sonarCloudConnection.isSonarQube()).isFalse();
    assertThat(sonarCloudConnection.getProductName()).isEqualTo("SonarQube Cloud");

    assertThat(sonarQubeConnection.isSonarCloud()).isFalse();
    assertThat(sonarQubeConnection.isSonarQube()).isTrue();
    assertThat(sonarQubeConnection.getProductName()).isEqualTo("SonarQube Server");
  }

  @Test
  void should_have_same_token_credentials() {
    var tokenConnection1 = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME)
      .setToken("same-token")
      .build();

    var tokenConnection2 = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME_2)
      .setToken("same-token")
      .build();

    assertThat(tokenConnection1.hasSameCredentials(tokenConnection2)).isTrue();
  }

  @Test
  void should_have_same_password_credentials() {
    var passwordConnection1 = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME)
      .setLogin("user")
      .setPassword("same-password")
      .build();

    var passwordConnection2 = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME_2)
      .setLogin("user")
      .setPassword("same-password")
      .build();

    assertThat(passwordConnection1.hasSameCredentials(passwordConnection2)).isTrue();
  }

  @Test
  void should_have_different_token_credentials() {
    var tokenConnection1 = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME)
      .setToken("same-token")
      .build();

    var differentTokenConnection = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME_2)
      .setToken("different-token")
      .build();

    assertThat(tokenConnection1.hasSameCredentials(differentTokenConnection)).isFalse();
  }

  /**
   * Helper method to create a ServerConnection with legacy PasswordUtil-encoded credentials.
   * This simulates what would happen when deserializing from XML with old format.
   */
  private ServerConnection createConnectionWithLegacyCredentials() throws Exception {
    // Create a basic connection using the builder (this will use fallback encoding in test environment)
    ServerConnection connection = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME)
      .setHostUrl("https://example.com")
      .setLogin("mylogin")
      .build();
    
    // Now override the fields with legacy encoded values to simulate XML deserialization
    setPrivateField(connection, "token", PasswordUtil.encodePassword("mytoken"));
    setPrivateField(connection, "password", PasswordUtil.encodePassword("mypassword"));
    
    return connection;
  }

  private ServerConnection createConnectionWithCorruptedCredentials() throws Exception {
    var connection = ServerConnection.newBuilder()
      .setName(CONNECTION_NAME)
      .setHostUrl("https://example.com")
      .build();
    
    // Set invalid base64 encoded values to simulate corrupted XML data
    setPrivateField(connection, "token", "invalid-base64-data");
    setPrivateField(connection, "password", "also-invalid-base64");
    
    return connection;
  }

  /**
   * Helper method to set private fields using reflection.
   */
  private void setPrivateField(Object object, String fieldName, Object value) throws Exception {
    var field = object.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(object, value);
    field.setAccessible(false);
  }

}
