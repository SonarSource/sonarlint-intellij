package org.sonarlint.intellij.util;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import javax.annotation.Nullable;

public class PasswordSafeUtil {

  private PasswordSafeUtil() {
    // Utility class
  }

  public static void storeServerAttributeToPasswordSafe(@Nullable String serverName, String key, @Nullable String value) {
    PasswordSafe passwordSafe = PasswordSafe.getInstance();
    serverName = serverName == null ? "" : serverName;
    String serviceName = CredentialAttributesKt.generateServiceName(serverName, key);
    CredentialAttributes attributes = new CredentialAttributes(serviceName);
    Credentials credentials = new Credentials(value, value);
    passwordSafe.set(attributes, credentials, false);
    passwordSafe.setPassword(attributes, value);
  }

  public static String getServerAttributeFromPasswordSafe(@Nullable String serverName, String key) {
    PasswordSafe passwordSafe = PasswordSafe.getInstance();
    serverName = serverName == null ? "" : serverName;
    String serviceName = CredentialAttributesKt.generateServiceName(serverName, key);
    CredentialAttributes attributes = new CredentialAttributes(serviceName);
    return passwordSafe.getPassword(attributes);
  }
}
