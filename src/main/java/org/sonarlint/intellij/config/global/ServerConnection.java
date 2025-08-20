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

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.swing.*;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.server.ServerLinks;
import org.sonarlint.intellij.core.server.SonarCloudLinks;
import org.sonarlint.intellij.core.server.SonarQubeLinks;

/**
 * Represents a connection configuration for SonarQube Server or SonarQube Cloud.
 * 
 * <h3>Security and Credential Storage</h3>
 * <p>This class implements secure credential storage using IntelliJ's {@link com.intellij.ide.passwordSafe.PasswordSafe} API.
 * Sensitive data (tokens and passwords) are stored in the operating system's native credential store.</p>
 * 
 * <h3>Migration and Backward Compatibility</h3>
 * <p>The class automatically migrates credentials from the legacy {@link com.intellij.openapi.util.PasswordUtil}
 * encoding to secure storage. For existing configurations:</p>
 * <ul>
 *   <li>Old base64-encoded credentials in XML are automatically migrated on first access</li>
 *   <li>After migration, only metadata is stored in XML; credentials remain in secure storage</li>
 *   <li>Fallback support maintains compatibility with test environments</li>
 * </ul>
 * 
 * <h3>XML Serialization</h3>
 * <p>This class is serialized in XML when SonarLintGlobalSettings is saved by IntelliJ.
 * By default, it will serialize data when there are public setters and getters for a field or when the field is public.
 * As this class is immutable, there are no setters and the fields are private, so nothing will be serialized by default.
 * Therefore, we must add the appropriate annotations for the fields we want to annotate.
 * Note that we use both {@link OptionTag} and {@link Tag} (which will result in 2 different ways of serializing the fields) to remain
 * backward-compatible with existing serialized configurations.</p>
 *
 * @see com.intellij.util.xmlb.annotations.Tag
 * @see com.intellij.util.xmlb.annotations.OptionTag
 * @see com.intellij.ide.passwordSafe.PasswordSafe
 */
@Immutable
// Don't change annotation, used for backward compatibility
@Tag("SonarQubeServer")
public class ServerConnection {
  @OptionTag
  private String region;
  @OptionTag
  private String hostUrl;
  @Tag
  private String token;
  @OptionTag
  private String name;
  @OptionTag
  private String login;
  @Tag
  private String password;
  @OptionTag
  private boolean enableProxy;
  @Tag
  private String organizationKey;
  @Tag
  private boolean disableNotifications;

  private ServerConnection() {
    // necessary for XML deserialization
  }

  private ServerConnection(Builder builder) {
    this.hostUrl = builder.hostUrl;
    this.token = builder.token;
    this.name = builder.name;
    this.login = builder.login;
    this.password = builder.password;
    this.enableProxy = builder.enableProxy;
    this.organizationKey = builder.organizationKey;
    this.disableNotifications = builder.disableNotifications;
    this.region = builder.region;
  }

  private static boolean isApplicationContextAvailable() {
    return ApplicationManager.getApplication() != null;
  }

  private static CredentialAttributes createCredentialAttributes(String key) {
    return new CredentialAttributes(
      CredentialAttributesKt.generateServiceName("SonarLint", key)
    );
  }

  private static String getTokenKeyFor(String name) {
    return "server:" + name + ":token";
  }

  private static String getPasswordKeyFor(String name) {
    return "server:" + name + ":password";
  }

  private String getPasswordFromCredentialStore() {
    if (name == null || !isApplicationContextAvailable()) return null;
    try {
      var attributes = createCredentialAttributes(getPasswordKeyFor(name));
      var credentials = PasswordSafe.getInstance().get(attributes);
      return credentials != null ? credentials.getPasswordAsString() : null;
    } catch (Exception e) {
      // Fall back gracefully if PasswordSafe is not available
      return null;
    }
  }

  private String getTokenFromCredentialStore() {
    if (name == null || !isApplicationContextAvailable()) return null;
    try {
      var attributes = createCredentialAttributes(getTokenKeyFor(name));
      return PasswordSafe.getInstance().getPassword(attributes);
    } catch (Exception e) {
      // Fall back gracefully if PasswordSafe is not available
      return null;
    }
  }

  private void storePasswordInCredentialStore(@Nullable String password) {
    if (name == null || !isApplicationContextAvailable()) return;
    try {
      var attributes = createCredentialAttributes(getPasswordKeyFor(name));
      if (password == null) {
        PasswordSafe.getInstance().set(attributes, null);
      } else {
        var credentials = new Credentials(login, password);
        PasswordSafe.getInstance().set(attributes, credentials);
      }
    } catch (Exception e) {
      // Fall back gracefully if PasswordSafe is not available
    }
  }

  private void storeTokenInCredentialStore(@Nullable String token) {
    if (name == null || !isApplicationContextAvailable()) return;
    try {
      var attributes = createCredentialAttributes(getTokenKeyFor(name));
      PasswordSafe.getInstance().setPassword(attributes, token);
    } catch (Exception e) {
      // Fall back gracefully if PasswordSafe is not available
    }
  }

  /**
   * Migrates credentials from the old PasswordUtil-encoded format to the new PasswordSafe format.
   * This method should be called during loading to ensure backward compatibility.
   */
  private void migrateCredentialsIfNeeded() {
    if (name == null || !isApplicationContextAvailable()) return;
    
    // Migrate token if it exists in old format
    if (token != null && getTokenFromCredentialStore() == null) {
      try {
        var decodedToken = PasswordUtil.decodePassword(token);
        storeTokenInCredentialStore(decodedToken);
        // Clear the old encoded token from XML
        this.token = null;
      } catch (NumberFormatException e) {
        // Ignore if decoding fails
      }
    }
    
    // Migrate password if it exists in old format
    if (password != null && getPasswordFromCredentialStore() == null) {
      try {
        var decodedPassword = PasswordUtil.decodePassword(password);
        storePasswordInCredentialStore(decodedPassword);
        // Clear the old encoded password from XML
        this.password = null;
      } catch (NumberFormatException e) {
        // Ignore if decoding fails
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ServerConnection other)) {
      return false;
    }

    return Objects.equals(getHostUrl(), other.getHostUrl()) &&
      Objects.equals(getPassword(), other.getPassword()) &&
      Objects.equals(getToken(), other.getToken()) &&
      Objects.equals(getLogin(), other.getLogin()) &&
      Objects.equals(getName(), other.getName()) &&
      Objects.equals(getOrganizationKey(), other.getOrganizationKey()) &&
      Objects.equals(enableProxy(), other.enableProxy()) &&
      Objects.equals(isDisableNotifications(), other.isDisableNotifications());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getHostUrl(), getPassword(), getToken(), getLogin(), getOrganizationKey(), getName(), enableProxy, disableNotifications);
  }

  public boolean isDisableNotifications() {
    return disableNotifications;
  }

  @CheckForNull
  public String getLogin() {
    return login;
  }

  public String getHostUrl() {
    return hostUrl;
  }

  @CheckForNull
  public String getRegion() {
    return region;
  }

  @CheckForNull
  public String getOrganizationKey() {
    return organizationKey;
  }

  @CheckForNull
  public String getToken() {
    migrateCredentialsIfNeeded();
    
    // Try to get from secure credential store first
    var tokenFromStore = getTokenFromCredentialStore();
    if (tokenFromStore != null) {
      return tokenFromStore;
    }
    
    // Fallback to old format for backward compatibility
    if (token == null) {
      return null;
    }
    try {
      return PasswordUtil.decodePassword(token);
    } catch (NumberFormatException e) {
      return null;
    }
  }
  public boolean isSonarCloud() {
    return SonarLintUtils.isSonarCloudAlias(hostUrl);
  }

  public boolean isSonarQube() {
    return !isSonarCloud();
  }

  public String getProductName() {
    return isSonarCloud() ? "SonarQube Cloud" : "SonarQube Server";
  }

  public Icon getProductIcon() {
    return isSonarCloud() ? SonarLintIcons.ICON_SONARQUBE_CLOUD_16 : SonarLintIcons.ICON_SONARQUBE_SERVER_16;
  }

  public boolean enableProxy() {
    return enableProxy;
  }

  @CheckForNull
  public String getPassword() {
    migrateCredentialsIfNeeded();
    
    // Try to get from secure credential store first
    var passwordFromStore = getPasswordFromCredentialStore();
    if (passwordFromStore != null) {
      return passwordFromStore;
    }
    
    // Fallback to old format for backward compatibility
    if (password == null) {
      return null;
    }
    try {
      return PasswordUtil.decodePassword(password);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public boolean hasSameCredentials(ServerConnection otherConnection) {
    if (token != null) {
      return Objects.equals(token, otherConnection.token);
    }
    return Objects.equals(password, otherConnection.password) && Objects.equals(login, otherConnection.login);
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public ServerLinks links() {
    return isSonarCloud() ? SonarCloudLinks.INSTANCE : new SonarQubeLinks(hostUrl);
  }

  public static class Builder {
    private String hostUrl;
    private String token;
    private String organizationKey;
    private String name;
    private String login;
    private String password;
    private boolean enableProxy;
    private boolean disableNotifications;
    private String region;

    private Builder() {
      // no args
    }

    public ServerConnection build() {
      var connection = new ServerConnection(this);
      
      // Store credentials in secure credential store for new connections when application context is available
      if (connection.name != null && isApplicationContextAvailable()) {
        if (this.token != null) {
          connection.storeTokenInCredentialStore(this.token);
          // Clear the plain token from the connection object to avoid storing it in XML
          connection.token = null;
        }
        if (this.password != null) {
          connection.storePasswordInCredentialStore(this.password);
          // Clear the plain password from the connection object to avoid storing it in XML
          connection.password = null;
        }
      } else {
        // In test environments or when application context is not available, fall back to the old encoding
        if (this.token != null) {
          connection.token = PasswordUtil.encodePassword(this.token);
        }
        if (this.password != null) {
          connection.password = PasswordUtil.encodePassword(this.password);
        }
      }
      
      return connection;
    }

    public Builder setLogin(@Nullable String login) {
      this.login = login;
      return this;
    }

    public Builder setDisableNotifications(boolean disableNotifications) {
      this.disableNotifications = disableNotifications;
      return this;
    }

    public Builder setOrganizationKey(@Nullable String organizationKey) {
      this.organizationKey = organizationKey;
      return this;
    }

    public Builder setHostUrl(String hostUrl) {
      this.hostUrl = hostUrl;
      return this;
    }

    public Builder setRegion(@Nullable String region) {
      this.region = region;
      return this;
    }

    public Builder setEnableProxy(boolean enableProxy) {
      this.enableProxy = enableProxy;
      return this;
    }

    public Builder setToken(@Nullable String token) {
      this.token = token;
      return this;
    }

    public Builder setPassword(@Nullable String password) {
      this.password = password;
      return this;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

  }

}
