package org.sonarlint.intellij.analysis;

public class AnalysisResult {
  private int filesAnalysed;
  private int issues;

  public AnalysisResult(int filesAnalysed, int issues) {
    this.filesAnalysed = filesAnalysed;
    this.issues = issues;
  }

  public int filesAnalysed() {
    return filesAnalysed;
  }

  public int issues() {
    return issues;
  }
}
