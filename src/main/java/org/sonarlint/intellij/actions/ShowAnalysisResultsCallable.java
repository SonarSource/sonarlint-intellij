package org.sonarlint.intellij.actions;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.issue.AnalysisResultIssues;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class ShowAnalysisResultsCallable implements AnalysisCallback {
  private final Project project;
  private final AnalysisResultIssues analysisResultIssues;
  private final Collection<VirtualFile> affectedFiles;
  private final IssueManager issueManager;

  public ShowAnalysisResultsCallable(Project project, Collection<VirtualFile> affectedFiles) {
    this.project = project;
    this.analysisResultIssues = SonarLintUtils.get(project, AnalysisResultIssues.class);
    this.issueManager = SonarLintUtils.get(project, IssueManager.class);
    this.affectedFiles = affectedFiles;
  }

  @Override public void onError(Throwable e) {
    // nothing to do
  }

  @Override
  public void onSuccess() {
    Map<VirtualFile, Collection<LiveIssue>> map = affectedFiles.stream()
      .collect(Collectors.toMap(Function.identity(), issueManager::getForFile));
    analysisResultIssues.set(map);
    showAllFilesTab();
  }

  private void showAllFilesTab() {
    UIUtil.invokeLaterIfNeeded(() -> ServiceManager.getService(project, IssuesViewTabOpener.class)
      .openProjectFiles());
  }
}
