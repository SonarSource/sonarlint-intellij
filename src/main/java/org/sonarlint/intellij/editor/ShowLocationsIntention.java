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
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.issue.LiveIssue;

public class ShowLocationsIntention implements IntentionAction, LowPriorityAction {
  private final RangeMarker primaryLocation;
  private final String message;
  private final List<LiveIssue.Flow> flows;

  public ShowLocationsIntention(RangeMarker primaryLocation, String message, List<LiveIssue.Flow> flows) {
    this.primaryLocation = primaryLocation;
    this.message = message;
    this.flows = flows;
  }

  @Nls @NotNull @Override public String getText() {
    return "Highlight all locations involved in this issue";
  }

  @Nls @NotNull @Override public String getFamilyName() {
    return "SonarLint locations";
  }

  @Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    SonarLintHighlighting h = ServiceManager.getService(project, SonarLintHighlighting.class);
    h.highlightFlowsWithHighlightersUtil(primaryLocation, message, flows);
  }

  @Override public boolean startInWriteAction() {
    return false;
  }
}
