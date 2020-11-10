/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.issue.IssueContext;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class ShowLocationsIntentionAction implements IntentionAction, PriorityAction, Iconable {
  private final LiveIssue issue;
  private final IssueContext context;

  public ShowLocationsIntentionAction(LiveIssue issue, IssueContext context) {
    this.issue = issue;
    this.context = context;
  }

  @Nls @NotNull @Override public String getText() {
    return "SonarLint: Show " + (context.hasUniqueFlow() ? "issue locations" : "data flows");
  }

  @Nls @NotNull @Override public String getFamilyName() {
    return "SonarLint locations";
  }

  @Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    SonarLintUtils.getService(project, SonarLintHighlighting.class).highlightIssue(issue);
    SonarLintToolWindow sonarLintToolWindow = SonarLintUtils.getService(project, SonarLintToolWindow.class);
    UIUtil.invokeLaterIfNeeded(() -> sonarLintToolWindow.showIssueLocations(issue));

  }

  @Override public boolean startInWriteAction() {
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
