package org.sonarlint.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.issue.AnalysisResultIssues;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class AnalysisResults {
  private final Project project;
  private final AnalysisResultIssues issues;

  public AnalysisResults(Project project) {
    this.project = project;
    this.issues = SonarLintUtils.get(project, AnalysisResultIssues.class);
  }
  public String getEmptyText() {
    if (issues.wasAnalyzed()) {
      return "No issues found";
    } else {
      return "No analysis done";
    }
  }

  @CheckForNull
  public Instant getLastAnalysisDate() {
    return issues.lastAnalysisDate();
  }

  public Map<VirtualFile, Collection<LiveIssue>> issues() {
    return issues.issues();
  }

  public String getLabelText() {
    return "Trigger an analysis to find issues in the project sources";
  }

}
