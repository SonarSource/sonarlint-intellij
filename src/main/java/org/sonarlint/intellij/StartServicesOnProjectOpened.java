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
package org.sonarlint.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.core.ConnectedModeStorageSynchronizer;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.server.events.ServerEventsService;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsRefreshTrigger;
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesRefreshTrigger;
import org.sonarlint.intellij.trigger.EditorChangeTrigger;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class StartServicesOnProjectOpened implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    getService(project, EditorChangeTrigger.class).onProjectOpened();
    if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
      getService(project, TaintVulnerabilitiesRefreshTrigger.class).subscribeToTriggeringEvents();
    }
    getService(project, SecurityHotspotsRefreshTrigger.class).subscribeToTriggeringEvents();
    doSubscribeForServerEvents(project);

    getService(BackendService.class).projectOpened(project);

    // perform on bindings load
    getService(project, ConnectedModeStorageSynchronizer.class).init();
  }

  void doSubscribeForServerEvents(@NotNull Project project) {
    var projectBinding = getService(project, ProjectBindingManager.class).getBinding();
    if (projectBinding != null) {
      getService(ServerEventsService.class).autoSubscribe(projectBinding);
    }
  }
}
