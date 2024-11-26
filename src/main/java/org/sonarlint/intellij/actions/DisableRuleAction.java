/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.util.DataKeys.ISSUE_DATA_KEY;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class DisableRuleAction extends AbstractSonarAction {

  public DisableRuleAction() {
    super("Disable Rule", "Disable the SonarQube for IDE rule that activated this rule", AllIcons.Actions.Cancel);
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    var project = e.getProject();
    var issue = e.getData(ISSUE_DATA_KEY);
    return project != null && !getSettingsFor(project).isBindingEnabled() && issue != null;
  }

  @Override
  protected boolean isEnabled(AnActionEvent e, Project project, AnalysisStatus status) {
    var issue = e.getData(ISSUE_DATA_KEY);
    return issue != null && !getGlobalSettings().isRuleExplicitlyDisabled(issue.getRuleKey());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    var project = e.getProject();
    if (project == null) {
      return;
    }

    var issue = e.getData(ISSUE_DATA_KEY);
    if (issue != null) {
      disableRule(issue.getRuleKey());
      runOnPooledThread(project, () -> getService(project, AnalysisSubmitter.class).autoAnalyzeOpenFiles(TriggerType.BINDING_UPDATE));
    }
  }

  private static void disableRule(String ruleKey) {
    getGlobalSettings().disableRule(ruleKey);
    var rulesByKey = getGlobalSettings().getRulesByKey();
    getService(BackendService.class).updateStandaloneRulesConfiguration(rulesByKey);
  }

}
