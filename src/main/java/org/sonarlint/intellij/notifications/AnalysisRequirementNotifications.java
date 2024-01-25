/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.notification.NotificationAction;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;

import static java.util.stream.Collectors.toSet;

public class AnalysisRequirementNotifications {

  private static final Set<String> alreadyNotified = new HashSet<>();

  private AnalysisRequirementNotifications() {
    // NOP
  }

  public static void resetCachedMessages() {
    alreadyNotified.clear();
  }

  public static void notifyOnceForSkippedPlugins(AnalysisResults analysisResults, Collection<PluginDetails> allPlugins, Project project) {
    var attemptedLanguages = analysisResults.languagePerFile().values()
      .stream()
      .filter(Objects::nonNull)
      .collect(toSet());
    attemptedLanguages.forEach(l -> {
      final var correspondingPlugin = allPlugins.stream().filter(p -> p.key().equals(l.getPluginKey())).findFirst();
      correspondingPlugin.flatMap(PluginDetails::skipReason).ifPresent(skipReason -> {
        if (skipReason instanceof SkipReason.UnsatisfiedRuntimeRequirement runtimeRequirement) {
          final var title = "<b>SonarLint failed to analyze " + l.getLabel() + " code</b>";
          if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE) {
            var content = String.format(
              "SonarLint requires Java runtime version %s or later to analyze %s code. Current version is %s.",
              runtimeRequirement.getMinVersion(), l.getLabel(), runtimeRequirement.getCurrentVersion());
            createNotificationOnce(project, title, content,
              new OpenLinkAction("https://intellij-support.jetbrains.com/hc/en-us/articles/206544879-Selecting-the-JDK-version-the-IDE-will-run-under",
                "How to change the IDE-running JDK?"));
          } else if (runtimeRequirement.getRuntime() == SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS) {
            var content = new StringBuilder(
              String.format("SonarLint requires Node.js runtime version %s or later to analyze %s code.", runtimeRequirement.getMinVersion(), l.getLabel()));
            if (runtimeRequirement.getCurrentVersion() != null) {
              content.append(String.format(" Current version is %s.", runtimeRequirement.getCurrentVersion()));
            }
            content.append("<br>Please configure the Node.js path in the SonarLint settings.");
            createNotificationOnce(project, title, content.toString(), new OpenGlobalSettingsAction(project));
          }
        }
      });
    });
  }

  private static void createNotificationOnce(Project project, String title, String content, NotificationAction... actions) {
    if (!alreadyNotified.contains(content)) {
      SonarLintProjectNotifications.Companion.get(project).createNotificationOnce(title, content, actions);
      alreadyNotified.add(content);
    }
  }

}
