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
package org.sonarlint.intellij.callable;

import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.AnalysisIntermediateResult;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.OnTheFlyFindingsHolder;
import org.sonarlint.intellij.common.util.SonarLintUtils;

public class ShowUpdatedCurrentFileCallable implements AnalysisCallback {
  private final Project project;
  private final UpdateOnTheFlyFindingsCallable updateOnTheFlyFindingsCallable;

  public ShowUpdatedCurrentFileCallable(Project project, OnTheFlyFindingsHolder onTheFlyFindingsHolder) {
    this.project = project;
    updateOnTheFlyFindingsCallable = new UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder);
  }

  @Override public void onError(Throwable e) {
    updateOnTheFlyFindingsCallable.onError(e);
  }

  @Override
  public void onIntermediateResult(AnalysisIntermediateResult intermediateResult) {
    updateOnTheFlyFindingsCallable.onIntermediateResult(intermediateResult);
  }

  @Override
  public void onSuccess(AnalysisResult analysisResult) {
    updateOnTheFlyFindingsCallable.onSuccess(analysisResult);
    showCurrentFileTab();
  }

  private void showCurrentFileTab() {
    var toolWindow = SonarLintUtils.getService(project, SonarLintToolWindow.class);
    toolWindow.openCurrentFileTab();
  }
}
