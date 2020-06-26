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
package org.sonarlint.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.core.ProjectServerNotifications;
import org.sonarlint.intellij.core.UpdateChecker;
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter;
import org.sonarlint.intellij.issue.persistence.LiveIssueCache;
import org.sonarlint.intellij.trigger.EditorChangeTrigger;
import org.sonarlint.intellij.util.SonarLintUtils;

public class BootstrapStartupActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {

    SonarLintUtils.getService(project, ProjectServerNotifications.class).init();
    SonarLintUtils.getService(project, CodeAnalyzerRestarter.class).init();
    SonarLintUtils.getService(project, EditorChangeTrigger.class).onProjectOpened();

    // perform on bindings load
    SonarLintUtils.getService(project, UpdateChecker.class).init();

    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        // Flush issues before project is closed, because we need to resolve module paths to compute the key
        SonarLintUtils.getService(project, LiveIssueCache.class).flushAll();
      }

    });

  }
}
