/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.FindingContext;
import org.sonarlint.intellij.finding.LiveFinding;

public class ShowLocationsIntentionAction implements IntentionAction, PriorityAction, Iconable {
  private final LiveFinding finding;
  private final FindingContext context;

  public ShowLocationsIntentionAction(LiveFinding finding, FindingContext context) {
    this.finding = finding;
    this.context = context;
  }

  @Nls @NotNull @Override public String getText() {
    return "SonarQube: Show " + (context.hasUniqueFlow() ? "issue locations" : "data flows");
  }

  @Nls @NotNull @Override public String getFamilyName() {
    return "SonarQube locations";
  }

  @Override public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override public void invoke(Project project, Editor editor, PsiFile file) {
    SonarLintUtils.getService(project, EditorDecorator.class).highlightFinding(finding);
    var sonarLintToolWindow = SonarLintUtils.getService(project, SonarLintToolWindow.class);
    sonarLintToolWindow.showFindingLocations(finding);
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
