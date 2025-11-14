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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;

class OpenConfigurableAction extends NotificationAction {
  private final Project project;
  private final Configurable configurable;

  OpenConfigurableAction(Project project, String text, Configurable configurable) {
    super(text);
    this.project = project;
    this.configurable = configurable;
  }

  @Override
  public final void actionPerformed(AnActionEvent e, Notification notification) {
    var closeNotification = true;
    if (!project.isDisposed()) {
      closeNotification = ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
    }
    if (closeNotification) {
      notification.expire();
    }
  }
}
