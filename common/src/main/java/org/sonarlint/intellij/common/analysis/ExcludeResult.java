package org.sonarlint.intellij.common.analysis;

import javax.annotation.Nullable;

public class ExcludeResult {
  private final boolean excluded;
  @Nullable
  private final String excludeReason;

  private ExcludeResult(boolean excluded, @Nullable String excludeReason) {
    this.excluded = excluded;
    this.excludeReason = excludeReason;
  }

  public boolean isExcluded() {
    return excluded;
  }

  public String excludeReason() {
    if (!excluded) {
      throw new UnsupportedOperationException("Not excluded");
    }
    return excludeReason;
  }

  public static ExcludeResult excluded(String excludeReason) {
    return new ExcludeResult(true, excludeReason);
  }

  public static ExcludeResult notExcluded() {
    return new ExcludeResult(false, null);
  }
}
