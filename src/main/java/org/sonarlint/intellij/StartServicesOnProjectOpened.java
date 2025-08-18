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
package org.sonarlint.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsRefreshTrigger;
import org.sonarlint.intellij.finding.sca.DependencyRisksRefreshTrigger;
import org.sonarlint.intellij.fs.EditorFileChangeListener;
import org.sonarlint.intellij.promotion.PromotionProvider;
import org.sonarlint.intellij.trigger.EditorOpenTrigger;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class StartServicesOnProjectOpened implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    runOnPooledThread(project, () -> {
      getService(EditorFileChangeListener.class).startListening();
      getService(project, EditorOpenTrigger.class).onProjectOpened();
      getService(project, SecurityHotspotsRefreshTrigger.class).subscribeToTriggeringEvents();
      getService(project, DependencyRisksRefreshTrigger.class).subscribeToTriggeringEvents();
      getService(project, PromotionProvider.class).subscribeToTriggeringEvents();
    });
  }
}
