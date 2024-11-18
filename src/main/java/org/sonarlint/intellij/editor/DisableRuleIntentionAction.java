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
package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class DisableRuleIntentionAction implements IntentionAction, LowPriorityAction, Iconable {
  private final String ruleKey;

  DisableRuleIntentionAction(String ruleKey) {
    this.ruleKey = ruleKey;
  }

  @Nls @NotNull @Override public String getText() {
    return "SonarQube: Disable rule '" + ruleKey + "'";
  }

  @Nls @NotNull @Override public String getFamilyName() {
    return "SonarQube disable rule";
  }

  @Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !isProjectConnected(project) && !isAlreadyDisabled();
  }

  @Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    getGlobalSettings().disableRule(ruleKey);
    var rulesByKey = getGlobalSettings().getRulesByKey();
    runOnPooledThread(project, () -> {
      getService(BackendService.class).updateStandaloneRulesConfiguration(rulesByKey);
      getService(project, AnalysisSubmitter.class).autoAnalyzeOpenFiles(TriggerType.BINDING_UPDATE);
    });
  }

  @Override public boolean startInWriteAction() {
    return false;
  }

  private static boolean isProjectConnected(Project project) {
    return getSettingsFor(project).isBindingEnabled();
  }

  private boolean isAlreadyDisabled() {
    return getGlobalSettings().isRuleExplicitlyDisabled(ruleKey);
  }

  @Override public Icon getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }
}
