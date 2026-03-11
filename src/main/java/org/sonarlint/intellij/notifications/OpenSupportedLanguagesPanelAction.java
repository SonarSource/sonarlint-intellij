/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.notifications;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable;

public class OpenSupportedLanguagesPanelAction extends NotificationAction {

  private final Project project;

  public OpenSupportedLanguagesPanelAction(Project project) {
    super("Open Supported Languages and Analyzers");
    this.project = project;
  }

  @Override
  public void actionPerformed(AnActionEvent e, Notification notification) {
    if (!project.isDisposed()) {
      var configurable = new SonarLintProjectConfigurable(project);
      var closeNotification = ShowSettingsUtil.getInstance().editConfigurable(project, configurable, configurable::selectSupportedLanguagesTab);
      if (closeNotification) {
        notification.expire();
      }
    }
  }
}
