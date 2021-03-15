/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ProjectBindingManager;

import static java.util.Optional.ofNullable;
import static org.sonarlint.intellij.util.SonarLintUtils.getService;

public class SonarLintProjectSettingsPanel implements Disposable {
  private final SonarLintProjectPropertiesPanel propsPanel;
  private final JPanel root;
  private final JPanel rootBindPane;
  private final JPanel rootPropertiesPane;
  private final ProjectExclusionsPanel exclusionsPanel;
  private SonarLintProjectBindPanel bindPanel;

  public SonarLintProjectSettingsPanel(Project project) {
    bindPanel = new SonarLintProjectBindPanel();
    propsPanel = new SonarLintProjectPropertiesPanel();
    exclusionsPanel = new ProjectExclusionsPanel(project);
    root = new JPanel(new BorderLayout());
    JBTabbedPane tabs = new JBTabbedPane();

    rootBindPane = new JPanel(new BorderLayout());
    rootBindPane.add(bindPanel.create(project));

    rootPropertiesPane = new JPanel(new BorderLayout());
    rootPropertiesPane.add(propsPanel.create(), BorderLayout.CENTER);

    tabs.insertTab("Bind to SonarQube / SonarCloud", null, rootBindPane, "Configure the binding to a SonarQube server or SonarCloud", 0);
    tabs.insertTab("File Exclusions", null, exclusionsPanel.getComponent(), "Configure which files to exclude from analysis", 1);
    tabs.insertTab("Analysis properties", null, rootPropertiesPane, "Configure analysis properties", 2);

    root.add(tabs, BorderLayout.CENTER);
  }

  public JPanel getRootPane() {
    return root;
  }

  public void load(List<ServerConnection> servers, SonarLintProjectSettings projectSettings) {
    propsPanel.setAnalysisProperties(projectSettings.getAdditionalProperties());
    bindPanel.load(servers, projectSettings.isBindingEnabled(), projectSettings.getConnectionName(), projectSettings.getProjectKey());
    exclusionsPanel.load(projectSettings);
  }

  public void save(Project project, SonarLintProjectSettings projectSettings) {
    projectSettings.setAdditionalProperties(propsPanel.getProperties());
    exclusionsPanel.save(projectSettings);

    ProjectBindingManager bindingManager = getService(project, ProjectBindingManager.class);
    ServerConnection connection = bindPanel.getSelectedConnection();
    String projectKey = bindPanel.getSelectedProjectKey();
    if (bindPanel.isBindingEnabled() && connection != null && projectKey != null) {
      bindingManager.bindTo(connection, projectKey);
    } else {
      bindingManager.unbind();
    }
  }

  private boolean bindingChanged(SonarLintProjectSettings projectSettings) {
    if (projectSettings.isBindingEnabled() ^ bindPanel.isBindingEnabled()) {
      return true;
    }

    if (bindPanel.isBindingEnabled()) {
      if (!StringUtils.equals(projectSettings.getConnectionName(), ofNullable(bindPanel.getSelectedConnection()).map(ServerConnection::getName).orElse(null))) {
        return true;
      }

      if (!StringUtils.equals(projectSettings.getProjectKey(), bindPanel.getSelectedProjectKey())) {
        return true;
      }
    }

    return false;
  }

  public boolean isModified(SonarLintProjectSettings projectSettings) {
    if (!propsPanel.getProperties().equals(projectSettings.getAdditionalProperties())) {
      return true;
    }

    if (isExclusionsModified(projectSettings)) {
      return true;
    }
    return bindingChanged(projectSettings);
  }

  public boolean isExclusionsModified(SonarLintProjectSettings projectSettings) {
    return exclusionsPanel.isModified(projectSettings);
  }

  @Override public void dispose() {
    if (bindPanel != null) {
      bindPanel = null;
    }
  }

  public void serversChanged(List<ServerConnection> serverList) {
    if (bindPanel != null) {
      bindPanel.connectionsChanged(serverList);
    }
  }
}
