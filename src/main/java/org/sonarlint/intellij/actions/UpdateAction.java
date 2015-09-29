package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.sonarlint.intellij.inspection.SonarQubeRunnerFacade;

public class UpdateAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    SonarQubeRunnerFacade runner = e.getProject().getComponent(SonarQubeRunnerFacade.class);
    runner.tryUpdate();
  }
}
