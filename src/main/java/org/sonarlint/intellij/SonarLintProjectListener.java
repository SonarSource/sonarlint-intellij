package org.sonarlint.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.core.SonarQubeEventNotifications;
import org.sonarlint.intellij.core.UpdateChecker;
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter;
import org.sonarlint.intellij.issue.persistence.LiveIssueCache;
import org.sonarlint.intellij.trigger.EditorChangeTrigger;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintProjectListener implements ProjectManagerListener {

  @Override
  public void projectOpened(@NotNull Project project) {
    SonarLintUtils.getService(project, SonarQubeEventNotifications.class).init();
    SonarLintUtils.getService(project, CodeAnalyzerRestarter.class).init();
    SonarLintUtils.getService(project, EditorChangeTrigger.class).onProjectOpened();
    SonarLintUtils.getService(project, ServerIssueUpdater.class).init();
    SonarLintUtils.getService(project, UpdateChecker.class).init();
  }

  @Override
  public void projectClosing(@NotNull Project project) {
    SonarLintUtils.getService(project, SonarLintConsole.class).dispose();
    SonarLintUtils.getService(project, SonarLintJobManager.class).dispose();
    SonarLintUtils.getService(project, SonarQubeEventNotifications.class).unregister();

    // Flush issues before project is closed, because we need to resolve module paths to compute the key
    SonarLintUtils.getService(project, LiveIssueCache.class).flushAll();
  }

  @Override
  public void projectClosed(@NotNull Project project) {
    SonarLintUtils.getService(project, EditorChangeTrigger.class).onProjectClosed();
    SonarLintUtils.getService(project, ServerIssueUpdater.class).dispose();
    SonarLintUtils.getService(project, UpdateChecker.class).onProjectClosed();

  }
}
