package org.sonarlint.intellij.analysis;

import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Map;
import java.util.stream.IntStream;
import org.sonarlint.intellij.issue.LiveIssue;

public class AnalysisResult {
  private int filesAnalysed;
  private Map<VirtualFile, Collection<LiveIssue>> issues;

  public AnalysisResult(int filesAnalysed, Map<VirtualFile, Collection<LiveIssue>> issues) {
    this.filesAnalysed = filesAnalysed;
    this.issues = issues;
  }

  public int filesAnalysed() {
    return filesAnalysed;
  }

  public int numberIssues() {
    return issues.entrySet().stream()
      .flatMapToInt(p -> IntStream.of(p.getValue().size()))
      .sum();
  }

  public Map<VirtualFile, Collection<LiveIssue>> issues() {
    return issues;
  }
}
