package org.sonarlint.intellij.analysis.cpd;

import java.util.List;

public class DuplicationReport {
  private final List<Duplication> blockDuplications;
  private final float density;

  public DuplicationReport(List<Duplication> blockDuplications, float density) {
    this.blockDuplications = blockDuplications;
    this.density = density;
  }

  public boolean hasAnyDuplication() {
    return !blockDuplications.isEmpty();
  }

  public List<Duplication> getBlockDuplications() {
    return blockDuplications;
  }

  public float getDensity() {
    return density;
  }
}
