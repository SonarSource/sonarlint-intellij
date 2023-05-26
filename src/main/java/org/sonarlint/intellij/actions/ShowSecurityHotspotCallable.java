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
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.AnalysisIntermediateResult;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.OnTheFlyFindingsHolder;
import org.sonarlint.intellij.notifications.ClearSecurityHotspotsFiltersAction;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

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
    runOnUiThread(project, () -> {
      var toolWindow = getService(project, SonarLintToolWindow.class);
      toolWindow.openSecurityHotspotsTab();
      toolWindow.bringToFront();

      boolean found = getService(project, SonarLintToolWindow.class).tryFindSecurityHotspot(securityHotspotKey);
      if (!found) {
        SonarLintProjectNotifications.get(project)
          .notifyUnableToOpenSecurityHotspot("The Security Hotspot could not be detected by SonarLint in the current code.");
      } else {
        boolean selected = getService(project, SonarLintToolWindow.class).trySelectSecurityHotspot(securityHotspotKey);
        if (!selected) {
          SonarLintProjectNotifications.get(project)
            .notifyUnableToOpenSecurityHotspot("The Security Hotspot could not be opened by SonarLint due to the applied filters.",
              new ClearSecurityHotspotsFiltersAction(securityHotspotKey));
        }
      }
    });
  }
}
