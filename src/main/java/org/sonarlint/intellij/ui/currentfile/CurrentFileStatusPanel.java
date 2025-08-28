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
package org.sonarlint.intellij.ui.currentfile;

import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.ui.WhatsInThisViewPanel;

public class CurrentFileStatusPanel extends JBPanel<CurrentFileStatusPanel> {

  public static final String HELP_TEXT = """
    This view analyzes the currently active file in real time and shows its findings.\s
    Taint vulnerabilities are provided by the server (not computed locally).\s
    Dependency risks are displayed at project level, as they are not linked to a specific file.
   """;

  private final Project project;

  CurrentFileStatusPanel(Project project) {
    super(new BorderLayout());
    this.project = project;
    createPanel();
  }

  private void createPanel() {
    add(new AutoTriggerStatusPanel(project).getPanel(), BorderLayout.WEST);

    add(new WhatsInThisViewPanel(project, HELP_TEXT).getPanel(), BorderLayout.CENTER);

    add(new CurrentFileConnectedModePanel(project).getPanel(), BorderLayout.EAST);
  }

  public static void subscribeToEventsThatAffectCurrentFile(Project project, Runnable runnable) {
    var busConnection = project.getMessageBus().connect();
    busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override
      public void applied(SonarLintGlobalSettings previousSettings, SonarLintGlobalSettings newSettings) {
        runnable.run();
      }
    });
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, (ProjectConfigurationListener) s -> runnable.run());
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(FileEditorManagerEvent event) {
        runnable.run();
      }
    });
  }

}
