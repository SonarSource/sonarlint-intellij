package org.sonarlint.intellij.util;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PasswordSafeUtilTest extends BasePlatformTestCase {

  @Test
  public void testPasswordSafeStoreNull() {
    PasswordSafeUtil.storeServerAttributeToPasswordSafe("name", "value", null);
    assertThat(PasswordSafeUtil.getServerAttributeFromPasswordSafe("name", "value")).isNull();
  }

  @Test
  public void testPasswordSafeStoreValue() {
    PasswordSafeUtil.storeServerAttributeToPasswordSafe("name", "value", "value");
    assertThat(PasswordSafeUtil.getServerAttributeFromPasswordSafe("name", "value")).isEqualTo("value");
  }

}
