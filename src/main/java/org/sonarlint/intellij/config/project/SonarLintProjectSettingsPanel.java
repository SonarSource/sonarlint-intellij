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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;
import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.config.global.SonarQubeServer;

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

  public void load(List<SonarQubeServer> servers, SonarLintProjectSettings projectSettings) {
    propsPanel.setAnalysisProperties(projectSettings.getAdditionalProperties());
    bindPanel.load(servers, projectSettings.isBindingEnabled(), projectSettings.getServerId(), projectSettings.getProjectKey());
    exclusionsPanel.load(projectSettings);
  }

  public void save(SonarLintProjectSettings projectSettings) {
    projectSettings.setAdditionalProperties(propsPanel.getProperties());
    projectSettings.setBindingEnabled(bindPanel.isBindingEnabled());
    exclusionsPanel.save(projectSettings);

    if (bindPanel.isBindingEnabled()) {
      projectSettings.setServerId(bindPanel.getSelectedStorageId());
      projectSettings.setProjectKey(bindPanel.getSelectedProjectKey());
    } else {
      projectSettings.setServerId(null);
      projectSettings.setProjectKey(null);
    }
  }

  private boolean bindingChanged(SonarLintProjectSettings projectSettings) {
    if (projectSettings.isBindingEnabled() ^ bindPanel.isBindingEnabled()) {
      return true;
    }

    if (bindPanel.isBindingEnabled()) {
      if (!StringUtils.equals(projectSettings.getServerId(), bindPanel.getSelectedStorageId())) {
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

  public void serversChanged(List<SonarQubeServer> serverList) {
    if (bindPanel != null) {
      bindPanel.serversChanged(serverList);
    }
  }
}
