package org.sonarlint.intellij.dogfood;

import com.intellij.openapi.util.PasswordUtil;

public class DogfoodCredentials {

  private String username;
  private String password;

  public DogfoodCredentials(String username, String password) {
    this.username = username;
    if (password != null) {
      this.password = PasswordUtil.encodePassword(password);
    }
  }

  public DogfoodCredentials() {
    // necessary for XML deserialization
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return PasswordUtil.decodePassword(password);
  }

}
