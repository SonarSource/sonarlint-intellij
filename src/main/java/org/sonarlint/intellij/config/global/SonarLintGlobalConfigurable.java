/*
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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBTabbedPane;
import java.awt.BorderLayout;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;

public class SonarLintGlobalConfigurable implements Configurable, Configurable.NoScroll {
  private final SonarLintEngineManager serverManager;
  private final Application app;
  private final SonarLintGlobalSettings globalSettings;
  private final SonarLintTelemetry telemetry;
  private final SonarApplication sonarApplication;
  private JPanel rootPanel;
  private SonarQubeServerMgmtPanel serversPanel;
  private SonarLintGlobalOptionsPanel globalPanel;
  private SonarLintAboutPanel about;
  private GlobalExclusionsPanel exclusions;

  public SonarLintGlobalConfigurable() {
    this.app = ApplicationManager.getApplication();
    this.globalSettings = app.getComponent(SonarLintGlobalSettings.class);
    this.serverManager = app.getComponent(SonarLintEngineManager.class);
    this.telemetry = app.getComponent(SonarLintTelemetry.class);
    this.sonarApplication = app.getComponent(SonarApplication.class);
  }

  @Nls @Override public String getDisplayName() {
    return "SonarLint General Settings";
  }

  @Nullable @Override public String getHelpTopic() {
    return null;
  }

  @Nullable @Override public JComponent createComponent() {
    return getPanel();
  }

  @Override public boolean isModified() {
    return serversPanel.isModified(globalSettings) || globalPanel.isModified(globalSettings)
      || about.isModified(telemetry) || exclusions.isModified(globalSettings);
  }

  @Override public void apply() throws ConfigurationException {
    serversPanel.save(globalSettings);
    globalPanel.save(globalSettings);
    about.save(telemetry);
    exclusions.save(globalSettings);

    GlobalConfigurationListener globalConfigurationListener = app.getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC);
    globalConfigurationListener.applied(globalSettings);
    serverManager.reloadServers();
  }

  @CheckForNull
  public List<SonarQubeServer> getCurrentSettings() {
    if (serversPanel != null) {
      SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
      serversPanel.save(settings);
      return settings.getSonarQubeServers();
    }

    return null;
  }

  @Override public void reset() {
    if (serversPanel != null) {
      serversPanel.load(globalSettings);
    }
    if (globalPanel != null) {
      globalPanel.load(globalSettings);
    }
    if (about != null) {
      about.load(telemetry);
    }
    if (exclusions != null) {
      exclusions.load(globalSettings);
    }
  }

  @Override public void disposeUIResources() {
    if (rootPanel != null) {
      rootPanel.setVisible(false);
      rootPanel = null;
    }
    if (serversPanel != null) {
      serversPanel.dispose();
      serversPanel = null;
    }
    about = null;
    serverManager.reloadServers();
  }

  private JPanel getPanel() {
    if (rootPanel == null) {
      about = new SonarLintAboutPanel(sonarApplication);
      exclusions = new GlobalExclusionsPanel();
      globalPanel = new SonarLintGlobalOptionsPanel();
      serversPanel = new SonarQubeServerMgmtPanel();

      JPanel settingsPanel = new JPanel(new BorderLayout());
      settingsPanel.add(globalPanel.getComponent(), BorderLayout.NORTH);
      settingsPanel.add(serversPanel.getComponent(), BorderLayout.CENTER);

      rootPanel = new JPanel(new BorderLayout());
      JBTabbedPane tabs = new JBTabbedPane();
      tabs.insertTab("Settings", null, settingsPanel, "Configure SonarLint for all projects", 0);
      tabs.insertTab("File Exclusions", null, exclusions.getComponent(), "Configure which files should be excluded from analysis", 1);
      tabs.insertTab("About", null, about.getComponent(), "About SonarLint", 2);
      rootPanel.add(tabs, BorderLayout.CENTER);
    }

    return rootPanel;
  }
}
