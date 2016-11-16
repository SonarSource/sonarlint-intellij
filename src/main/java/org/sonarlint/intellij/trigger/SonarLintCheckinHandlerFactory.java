package org.sonarlint.intellij.trigger;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;

public class SonarLintCheckinHandlerFactory extends CheckinHandlerFactory {
  private final SonarLintGlobalSettings globalSettings;

  public SonarLintCheckinHandlerFactory(SonarLintGlobalSettings globalSettings) {
    this.globalSettings = globalSettings;
  }

  @NotNull @Override public CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    return new SonarLintCheckinHandler(ToolWindowManager.getInstance(panel.getProject()), globalSettings, panel.getVirtualFiles(), panel.getProject());
  }
}
