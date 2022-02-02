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
package org.sonarlint.intellij.ui;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;

public class CurrentFileStatusPanel {

  private final Project project;

  private JPanel panel;

  CurrentFileStatusPanel(Project project) {
    this.project = project;
    createPanel();
  }

  private void createPanel() {
    panel = new JPanel(new BorderLayout());
    panel.add(new AutoTriggerStatusPanel(project).getPanel(), BorderLayout.CENTER);
    panel.add(new CurrentFileConnectedModePanel(project).getPanel(), BorderLayout.EAST);
  }

  static void subscribeToEventsThatAffectCurrentFile(Project project, Runnable runnable) {
    var busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override
      public void applied(SonarLintGlobalSettings previousSettings, SonarLintGlobalSettings newSettings) {
        runnable.run();
      }
    });
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, s -> runnable.run());
    busConnection.subscribe(PowerSaveMode.TOPIC, runnable::run);
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        runnable.run();
      }
    });
  }

  public JPanel getPanel() {
    return panel;
  }
}
