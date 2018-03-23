package org.sonarlint.intellij.actions;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.sonarlint.intellij.analysis.AnalysisCallback;

public class ShowCurrentFileCallable implements AnalysisCallback {
  private final Project project;

  public ShowCurrentFileCallable(Project project) {
    this.project = project;
  }

  @Override public void onError(Throwable e) {
    // do nothing
  }

  @Override
  public void onSuccess() {
    showCurrentFileTab();
  }

  private void showCurrentFileTab() {
    UIUtil.invokeLaterIfNeeded(() -> ServiceManager.getService(project, IssuesViewTabOpener.class).openCurrentFile());
  }
}
