/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import javax.swing.Icon;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;

public class SonarAnalyzeChangedFilesAction extends AbstractSonarAction {
  public SonarAnalyzeChangedFilesAction() {
    super();
  }

  public SonarAnalyzeChangedFilesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override protected boolean isEnabled(AnActionEvent e, Project project, AnalysisStatus status) {
    if (status.isRunning()) {
      return false;
    }
    var changeListManager = ChangeListManager.getInstance(project);
    return !changeListManager.getAffectedFiles().isEmpty();
  }

  @Override
  protected boolean isVisible(String place) {
    return !ActionPlaces.PROJECT_VIEW_POPUP.equals(place);
  }

  @Override public void actionPerformed(AnActionEvent e) {
    var project = e.getProject();

    if (project == null || ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace())) {
      return;
    }

    var submitter = SonarLintUtils.getService(project, SonarLintSubmitter.class);
    var changeListManager = ChangeListManager.getInstance(project);

    var affectedFiles = changeListManager.getAffectedFiles();
    var callback = new ShowAnalysisResultsCallable(project, affectedFiles, "SCM changed files");
    submitter.submitFiles(affectedFiles, TriggerType.CHANGED_FILES, callback, false);
  }
}
