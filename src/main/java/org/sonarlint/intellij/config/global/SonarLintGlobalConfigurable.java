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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.components.JBTabbedPane;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.sonarlint.intellij.config.global.rules.RuleConfigurationPanel;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintUtils;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class SonarLintGlobalConfigurable implements Configurable, Configurable.NoScroll {
  private static final int SETTINGS_TAB_INDEX = 0;
  private static final int FILE_EXCLUSIONS_TAB_INDEX = 1;
  private static final int RULES_TAB_INDEX = 2;
  private static final int ABOUT_TAB_INDEX = 3;
  private JPanel rootPanel;
  private SonarQubeServerMgmtPanel serversPanel;
  private SonarLintGlobalOptionsPanel globalPanel;
  private SonarLintAboutPanel about;
  private GlobalExclusionsPanel exclusions;
  private RuleConfigurationPanel rules;
  private JBTabbedPane tabs;

  @Nls
  @Override
  public String getDisplayName() {
    return "SonarLint";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return getPanel();
  }

  @Override
  public boolean isModified() {
    SonarLintGlobalSettings globalSettings = getGlobalSettings();
    SonarLintTelemetry telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);
    return serversPanel.isModified(globalSettings) || globalPanel.isModified(globalSettings)
      || about.isModified(telemetry) || exclusions.isModified(globalSettings) || rules.isModified(globalSettings);
  }

  @Override
  public void apply() {
    SonarLintGlobalSettings globalSettings = getGlobalSettings();
    SonarLintTelemetry telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);
    final boolean exclusionsModified = exclusions.isModified(globalSettings);
    final boolean rulesModified = rules.isModified(globalSettings);
    final String previousNodeJsPath = globalSettings.getNodejsPath();

    serversPanel.save(globalSettings);
    globalPanel.save(globalSettings);
    about.save(telemetry);
    rules.save(globalSettings);
    exclusions.save(globalSettings);

    GlobalConfigurationListener globalConfigurationListener = ApplicationManager.getApplication()
      .getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC);
    globalConfigurationListener.applied(globalSettings);
    SonarLintUtils.getService(SonarLintEngineManager.class).stopAllDeletedConnectedEngines();

    if (!Objects.equals(previousNodeJsPath, globalSettings.getNodejsPath())) {
      // Node.js path is passed at engine startup, so we have to restart them all to ensure the new value is taken into account
      SonarLintUtils.getService(SonarLintEngineManager.class).stopAllEngines();
    }

    if (exclusionsModified) {
      analyzeOpenFiles(false);
    } else if (rulesModified) {
      analyzeOpenFiles(true);
    }
  }

  private static void analyzeOpenFiles(boolean unboundOnly) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

    for (Project project : openProjects) {
      if (!unboundOnly || !getSettingsFor(project).isBindingEnabled()) {
        SonarLintUtils.getService(project, SonarLintSubmitter.class).submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
      }
    }
  }

  @CheckForNull
  public List<SonarQubeServer> getCurrentServers() {
    if (serversPanel != null) {
      return serversPanel.getServers();
    }

    return null;
  }

  @Override
  public void reset() {
    SonarLintGlobalSettings globalSettings = getGlobalSettings();
    SonarLintTelemetry telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);

    serversPanel.load(globalSettings);
    globalPanel.load(globalSettings);
    about.load(telemetry);
    rules.load(globalSettings);
    exclusions.load(globalSettings);
  }

  @Override
  public void disposeUIResources() {
    if (rootPanel != null) {
      rootPanel.setVisible(false);
      rootPanel = null;
    }
    if (serversPanel != null) {
      serversPanel.dispose();
      serversPanel = null;
    }
    about = null;
    rules = null;
    SonarLintUtils.getService(SonarLintEngineManager.class).stopAllDeletedConnectedEngines();
  }

  private JPanel getPanel() {
    if (rootPanel == null) {
      about = new SonarLintAboutPanel();
      rules = new RuleConfigurationPanel();
      exclusions = new GlobalExclusionsPanel();
      globalPanel = new SonarLintGlobalOptionsPanel();
      serversPanel = new SonarQubeServerMgmtPanel();

      JPanel settingsPanel = new JPanel(new BorderLayout());
      settingsPanel.add(globalPanel.getComponent(), BorderLayout.NORTH);
      settingsPanel.add(serversPanel.getComponent(), BorderLayout.CENTER);

      rootPanel = new JPanel(new BorderLayout());
      tabs = new JBTabbedPane();
      tabs.insertTab("Settings", null, settingsPanel, "Configure SonarLint for all projects", SETTINGS_TAB_INDEX);
      tabs.insertTab("File Exclusions", null, exclusions.getComponent(), "Configure which files should be excluded from analysis", FILE_EXCLUSIONS_TAB_INDEX);
      tabs.insertTab("Rules", null, rules.getComponent(), "Choose which rules are enabled when not connected to SonarQube", RULES_TAB_INDEX);
      tabs.insertTab("About", null, about.getComponent(), "About SonarLint", ABOUT_TAB_INDEX);
      rootPanel.add(tabs, BorderLayout.CENTER);
    }

    return rootPanel;
  }

  public void selectRule(String ruleKey) {
    tabs.setSelectedIndex(RULES_TAB_INDEX);
    rules.selectRule(ruleKey);
  }
}
