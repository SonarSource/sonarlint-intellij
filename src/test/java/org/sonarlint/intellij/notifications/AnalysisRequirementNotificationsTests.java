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

import com.google.common.collect.Lists;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsManager;
import com.intellij.util.messages.MessageBusConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisRequirementNotificationsTests extends AbstractSonarLintLightTests {

  final AnalysisResults analysisResults = mock(AnalysisResults.class);
  final Map<ClientInputFile, SonarLanguage> detectedLang = new HashMap<>();

  private List<Notification> notifications;
  private @NotNull MessageBusConnection busConnection;

  @BeforeEach
  void before() {
    AnalysisRequirementNotifications.resetCachedMessages();

    // register a listener to catch all notifications
    notifications = Lists.newCopyOnWriteArrayList();
    var project = getProject();
    busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(Notifications.TOPIC, new Notifications() {
      @Override
      public void notify(@NotNull Notification notification) {
        notifications.add(notification);
      }
    });

    when(analysisResults.languagePerFile()).thenReturn(detectedLang);
  }

  /**
   * Expire notifications created in test methods.
   */
  @AfterEach
  void expireAfterTest() {
    var mgr = NotificationsManager.getNotificationsManager();
    var notifications = mgr.getNotificationsOfType(Notification.class, getProject());
    Stream.of(notifications).forEach(mgr::expire);
    busConnection.disconnect();
  }

  @Test
  void notifyIfSkippedLanguage_JRE() {
    AnalysisRequirementNotifications.notifyOnceForSkippedPlugins(getProject(), Language.JAVA, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE,
      "11", "1.8");
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).getContent()).isEqualTo("SonarLint requires Java runtime version 11 or later to analyze Java code. Current version is 1.8.");
  }

  @Test
  void notifyIfSkippedLanguage_Node() {
    AnalysisRequirementNotifications.notifyOnceForSkippedPlugins(getProject(), Language.JS, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_NODE_JS,
      "8.0", "7.2");
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).getContent()).isEqualTo("SonarLint requires Node.js runtime version 8.0 or later to analyze JavaScript code. Current version is 7.2.<br>Please configure the Node.js path in the SonarLint settings.");
    assertThat(notifications.get(0).getActions()).hasSize(1);
    assertThat(notifications.get(0).getActions().get(0).getTemplatePresentation().getText()).isEqualTo("Open SonarLint Settings");
  }

}
