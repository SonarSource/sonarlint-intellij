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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.AnalysisIntermediateResult;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.OnTheFlyFindingsHolder;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class ShowSecurityHotspotCallable implements AnalysisCallback {
  private final Project project;
  private final UpdateOnTheFlyFindingsCallable updateOnTheFlyFindingsCallable;
  private final String securityHotspotKey;

  public ShowSecurityHotspotCallable(Project project, OnTheFlyFindingsHolder onTheFlyFindingsHolder, String securityHotspotKey) {
    this.project = project;
    this.updateOnTheFlyFindingsCallable = new UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder);
    this.securityHotspotKey = securityHotspotKey;
  }

  @Override
  public void onError(Throwable e) {
    updateOnTheFlyFindingsCallable.onError(e);
  }

  @Override
  public void onIntermediateResult(AnalysisIntermediateResult intermediateResult) {
    updateOnTheFlyFindingsCallable.onIntermediateResult(intermediateResult);
  }

  @Override
  public void onSuccess(AnalysisResult analysisResult) {
    updateOnTheFlyFindingsCallable.onSuccess(analysisResult);
    showSecurityHotspotsTab();
  }

  private void showSecurityHotspotsTab() {
    UIUtil.invokeLaterIfNeeded(() -> {
      var toolWindow = getService(project, SonarLintToolWindow.class);
      toolWindow.openSecurityHotspotsTab();
      toolWindow.bringToFront();
      boolean success = getService(project, SonarLintToolWindow.class).trySelectSecurityHotspot(securityHotspotKey);
      if (!success) {
        SonarLintProjectNotifications.get(project)
          .notifyUnableToOpenSecurityHotspot("The Security Hotspot you tried to open could not be detected by SonarLint in the current code.");
      }
    });
  }
}
