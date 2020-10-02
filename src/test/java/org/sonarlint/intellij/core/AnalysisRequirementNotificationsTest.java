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

import com.google.common.collect.Lists;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsAdapter;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.SkipReason;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AnalysisRequirementNotificationsTest extends AbstractSonarLintLightTests {

  final AnalysisResults analysisResults = mock(AnalysisResults.class);
  final Map<ClientInputFile, Language> detectedLang = new HashMap<>();

  private List<Notification> notifications;

  @Before
  public void before() {
    AnalysisRequirementNotifications.resetCachedMessages();

    // register a listener to catch all notifications
    notifications = Lists.newCopyOnWriteArrayList();
    Project project = getProject();
    project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, new NotificationsAdapter() {
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
  @After
  public void expireAfterTest() {
    NotificationsManager mgr = NotificationsManager.getNotificationsManager();
    Notification[] notifications = mgr.getNotificationsOfType(Notification.class, getProject());
    for (Notification notification : notifications) {
      mgr.expire(notification);
    }
  }

  @Test
  public void do_nothing_if_no_files() {
    AnalysisRequirementNotifications.notifyOnceForSkippedPlugins(mock(AnalysisResults.class), Collections.emptyList(), getProject());

    assertThat(notifications).isEmpty();
  }

  // SLI-453
  @Test
  public void dont_fail_if_null_language() {
    detectedLang.put(mock(ClientInputFile.class), null);
    AnalysisRequirementNotifications.notifyOnceForSkippedPlugins(analysisResults, Collections.emptyList(), getProject());

    assertThat(notifications).isEmpty();
  }

  @Test
  public void notifyIfSkippedLanguage_JRE() {
    detectedLang.put(mock(ClientInputFile.class), Language.JAVA);
    List<PluginDetails> plugins = Collections.singletonList(new FakePluginDetails("java", "Java", "1.0", new SkipReason.UnsatisfiedRuntimeRequirement(SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE, "1.8", "11")));
    AnalysisRequirementNotifications.notifyOnceForSkippedPlugins(analysisResults, plugins, getProject());
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).getContent()).isEqualTo("SonarLint requires Java runtime version 11 or later to analyze Java code. Current version is 1.8.<br>See <a href=\"https://intellij-support.jetbrains.com/hc/en-us/articles/206544879-Selecting-the-JDK-version-the-IDE-will-run-under\">how to select the JDK version the IDE will run under</a>.");
  }

  @Test
  public void notifyIfSkippedLanguage_Node() {
    detectedLang.put(mock(ClientInputFile.class), Language.JS);
    List<PluginDetails> plugins = Collections.singletonList(new FakePluginDetails("javascript", "JS/TS", "1.0", new SkipReason.UnsatisfiedRuntimeRequirement(SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.NODEJS, "7.2", "8.0")));
    AnalysisRequirementNotifications.notifyOnceForSkippedPlugins(analysisResults, plugins, getProject());
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).getContent()).isEqualTo("SonarLint requires Node.js runtime version 8.0 or later to analyze JavaScript code. Current version is 7.2.<br>Please configure the Node.js path in the <a href=\"#edit-settings\">SonarLint settings</a>.");
  }

  private static class FakePluginDetails implements PluginDetails {

    private final String key;
    private final String name;
    private final String version;
    @Nullable
    private final SkipReason skipReason;

    public FakePluginDetails(String key, String name, String version, @Nullable SkipReason skipReason) {
      this.key = key;
      this.name = name;
      this.version = version;
      this.skipReason = skipReason;
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String version() {
      return version;
    }

    @Override
    public Optional<SkipReason> skipReason() {
      return Optional.ofNullable(skipReason);
    }

  }


}
