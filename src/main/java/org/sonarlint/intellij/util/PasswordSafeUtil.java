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
package org.sonarlint.intellij.util;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

public class PasswordSafeUtil {

  private PasswordSafeUtil() {
    // Utility class
  }

  public static void putServerAttributeInMemorySafe(@Nullable String serverName, String key, String value) {
    PasswordSafe passwordSafe = PasswordSafe.getInstance();
    CredentialAttributes attributes = getCredentialAttributes(serverName, key);
    Credentials credentials = new Credentials(null, value);
    // Only store in memory
    passwordSafe.set(attributes, credentials, true);
  }

  public static void replaceServerAttributeInPasswordSafe(@Nullable String serverName, String key, @Nullable String value) {
    PasswordSafe passwordSafe = PasswordSafe.getInstance();
    CredentialAttributes attributes = getCredentialAttributes(serverName, key);
    Credentials credentials = new Credentials(null, value);
    // Clear old value
    passwordSafe.set(attributes, null, false);
    // Set new value
    passwordSafe.set(attributes, credentials, false);
  }

  @CheckForNull
  public static String getServerAttributeFromPasswordSafe(@Nullable String serverName, String key) {
    PasswordSafe passwordSafe = PasswordSafe.getInstance();
    CredentialAttributes attributes = getCredentialAttributes(serverName, key);
    return passwordSafe.getPassword(attributes);
  }

  @NotNull
  private static CredentialAttributes getCredentialAttributes(@Nullable String serverName, String key) {
    String serviceName = CredentialAttributesKt.generateServiceName("SonarLint connected mode " + nullSafeServerName(serverName), key);
    return new CredentialAttributes(serviceName);
  }

  @NotNull
  private static String nullSafeServerName(@Nullable String serverName) {
    return serverName == null ? "" : serverName;
  }
}
