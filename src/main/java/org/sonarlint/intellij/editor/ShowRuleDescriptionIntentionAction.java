/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.persistence.FindingsManager;

public class ShowRuleDescriptionIntentionAction implements IntentionAction, PriorityAction, Iconable {

  private final String ruleKey;

  public ShowRuleDescriptionIntentionAction(String ruleKey) {
    this.ruleKey = ruleKey;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
    return "SonarLint: Show rule description '" + ruleKey + "'";
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getFamilyName() {
    return "SonarLint show issue description";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    var actualFile = file.getVirtualFile();
    if (editor instanceof EditorEx) {
      // SLI-633 - When PhpStorm detects that a string is used as a regular expression, it injects a language reference that leads the file
      // passed here to be a completely virtual 'PHP_REGEXP_FILE' instance. However, the editor holds the reference to the actual file.
      actualFile = ((EditorEx) editor).getVirtualFile();
    }
    var issueManager = SonarLintUtils.getService(project, FindingsManager.class);
    var liveIssues = issueManager.getIssuesForFile(actualFile);
    var liveIssue = liveIssues.stream().filter(issue -> issue.getRuleKey().equals(ruleKey)).findFirst();
    if (liveIssue.isEmpty()) {
      return;
    }
    UIUtil.invokeLaterIfNeeded(() -> SonarLintUtils.getService(project, SonarLintToolWindow.class)
      .showIssueDescription(liveIssue.get()));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Forward;
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.NORMAL;
  }
}
