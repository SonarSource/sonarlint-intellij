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

package org.sonarlint.intellij.core;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;

public class AnalysisRequirementNotifications {

  private static final NotificationGroup ANALYZER_REQUIREMENT_GROUP = NotificationGroup.balloonGroup("SonarLint: Analyzer Requirement");

  private static final Set<String> alreadyNotified = new HashSet<>();

  private AnalysisRequirementNotifications() {
    // NOP
  }

  public static void resetCachedMessages() {
    alreadyNotified.clear();
  }

  public static void notifyOnceForSkippedPlugins(AnalysisResults analysisResults, Collection<PluginDetails> allPlugins, Project project) {
    Set<Language> attemptedLanguages = new HashSet<>(analysisResults.languagePerFile().values());
    attemptedLanguages.forEach(l -> {
      final Optional<PluginDetails> correspondingPlugin = allPlugins.stream().filter(p -> p.key().equals(l.getPluginKey())).findFirst();
      correspondingPlugin.flatMap(PluginDetails::skipReason).ifPresent(skipReason -> {
        if (skipReason instanceof SkipReason.UnsatisfiedRuntimeRequirement) {
          final SkipReason.UnsatisfiedRuntimeRequirement runtimeRequirement = (SkipReason.UnsatisfiedRuntimeRequirement) skipReason;
          final String title = "<b>SonarLint failed to analyze " + l.getLabel() + " code</b>";
          if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE) {
            String content = String.format("SonarLint requires Java runtime version %s or later to analyze %s code. Current version is %s.<br>See <a href=\"https://intellij-support.jetbrains.com/hc/en-us/articles/206544879-Selecting-the-JDK-version-the-IDE-will-run-under\">how to select the JDK version the IDE will run under</a>.",
              runtimeRequirement.getMinVersion(), l.getLabel(), runtimeRequirement.getCurrentVersion());
            createNotificationOnce(project, title, content, new NotificationListener.UrlOpeningListener(true));
          } else if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS) {
            StringBuilder content = new StringBuilder(String.format("SonarLint requires Node.js runtime version %s or later to analyze %s code.", runtimeRequirement.getMinVersion(), l.getLabel()));
            if (runtimeRequirement.getCurrentVersion() != null) {
              content.append(String.format(" Current version is %s.", runtimeRequirement.getCurrentVersion()));
            }
            content.append("<br>Please configure the Node.js path in the <a href=\"#edit-settings\">SonarLint settings</a>.");
            createNotificationOnce(project, title, content.toString(), new OpenGlobalSettingsNotificationListener(project));
          }
        }
      });
    });
  }

  private static void createNotificationOnce(Project project, String title, String content, NotificationListener listener) {
    if (!alreadyNotified.contains(content)) {
      Notification notification = ANALYZER_REQUIREMENT_GROUP.createNotification(
        title,
        content,
        NotificationType.WARNING, listener);
      notification.setImportant(true);
      notification.notify(project);
      alreadyNotified.add(content);
    }
  }

  private static class OpenGlobalSettingsNotificationListener extends NotificationListener.Adapter {
    private final Project project;

    private OpenGlobalSettingsNotificationListener(@Nullable Project project) {
      this.project = project;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      if (project != null && !project.isDisposed()) {
        SonarLintGlobalConfigurable configurable = new SonarLintGlobalConfigurable();
        ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
      } else {
        notification.expire();
      }
    }
  }

  /**
   * For old versions of SonarJS before the requirement was stored in the MANIFEST
   */
  public static void notifyNodeCommandException(Project project) {
    Notification notification = ANALYZER_REQUIREMENT_GROUP.createNotification(
      "<b>SonarLint - Node.js Required</b>",
      "Node.js >= 8.x is required to perform JavaScript or TypeScript analysis. Check the <a href='#'>SonarLint Log</a> for details.",
      NotificationType.WARNING, new ShowSonarLintLogListener(project));
    notification.setImportant(true);
    notification.notify(project);
  }

  private static class ShowSonarLintLogListener implements NotificationListener {
    private final Project project;
    private ShowSonarLintLogListener(Project project) {
      this.project = project;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      notification.expire();
      ToolWindow toolWindow = getToolWindow();
      if (toolWindow != null) {
        toolWindow.show(() -> selectLogsTab(toolWindow));
      }
    }

    private ToolWindow getToolWindow() {
      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
      return toolWindowManager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
    }

    private static void selectLogsTab(ToolWindow toolWindow) {
      ContentManager contentManager = toolWindow.getContentManager();
      Content content = contentManager.findContent(SonarLintToolWindowFactory.TAB_LOGS);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }

  }
}
