/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.core;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable;

public class SonarLintProjectNotifications extends AbstractProjectComponent {
  public static final String BINDING_PROBLEM = "SonarLint: Server Binding Errors";
  private volatile boolean shown = false;

  protected SonarLintProjectNotifications(Project project) {
    super(project);
  }

  public static SonarLintProjectNotifications get(Project project) {
    return project.getComponent(SonarLintProjectNotifications.class);
  }

  public void reset() {
    shown = false;
  }

  public void notifyServerIdInvalid() {
    if(shown) {
      return;
    }
    Notification notification = new Notification(BINDING_PROBLEM,
      "SonarLint - Project bound to invalid SonarQube server",
      "Please check the <a href='#'>SonarLint project configuration</a>",
      NotificationType.ERROR, new OpenProjectSettingsNotificationListener(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  public void notifyServerNotUpdated() {
    if(shown) {
      return;
    }
    Notification notification = new Notification(BINDING_PROBLEM,
      "SonarLint - No data for SonarQube server",
      "Please update the SonarQube server in the <a href='#'>SonarLint project configuration</a>",
      NotificationType.ERROR, new OpenProjectSettingsNotificationListener(myProject));
    notification.setImportant(true);
    notification.notify(myProject);
    shown = true;
  }

  private static class OpenProjectSettingsNotificationListener extends NotificationListener.Adapter {
    private final Project project;

    public OpenProjectSettingsNotificationListener(Project project) {
      this.project = project;
    }
    @Override protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      if(project != null && !project.isDisposed()) {
        SonarLintProjectConfigurable configurable = new SonarLintProjectConfigurable(project);
        ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
      } else {
        notification.expire();
      }
    }
  }
}
