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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.apache.commons.codec.binary.StringUtils;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;

public class SonarLintProjectSettingsPanel {
  private SonarLintProjectBindPanel bindPanel;
  private SonarLintProjectPropertiesPanel propsPanel;

  private JPanel root;
  private JPanel rootBindPane;
  private JPanel rootPropertiesPane;
  private Project project;

  public SonarLintProjectSettingsPanel(Project project) {
    this.project = project;
    this.bindPanel = new SonarLintProjectBindPanel();
    this.propsPanel = new SonarLintProjectPropertiesPanel();

    root = new JPanel(new BorderLayout());
    JBTabbedPane tabs = new JBTabbedPane();

    rootBindPane = new JPanel(new BorderLayout());
    rootBindPane.add(bindPanel.create());

    rootPropertiesPane = new JPanel(new BorderLayout());
    rootPropertiesPane.add(propsPanel.create(), BorderLayout.CENTER);

    tabs.insertTab("Bind to SonarQube project", null, rootBindPane, "Configure the binding of modules to a SonarQube server", 0);
    tabs.insertTab("Analysis properties", null, rootPropertiesPane, "Configure analysis properties", 1);

    root.add(tabs, BorderLayout.CENTER);
  }

  public JPanel getRootPane() {
    return root;
  }

  public void load(SonarLintGlobalSettings globalSettings, SonarLintProjectSettings projectSettings) {
    propsPanel.setAnalysisProperties(projectSettings.getAdditionalProperties());
    bindPanel.load(globalSettings.getSonarQubeServers(), projectSettings.isBindingEnabled(),
      projectSettings.rootModulesOnly(), projectSettings.getServerId(), projectSettings.getProjectKey());
  }

  public void save(SonarLintProjectSettings projectSettings) {
    projectSettings.setAdditionalProperties(propsPanel.getProperties());
    projectSettings.setServerId(bindPanel.getSelectedStorageId());
    projectSettings.setProjectKey(bindPanel.getSelectedProjectKey());
    projectSettings.setBindingEnabled(bindPanel.isBindingEnabled());
    projectSettings.setTopLevelOnly(bindPanel.rootModulesOnly());

    if(projectSettings.isBindingEnabled()) {
      bindPanel.actionUpdateProjectTask();
    }
  }

  public boolean isModified(SonarLintProjectSettings projectSettings) {
    if (!propsPanel.getProperties().equals(projectSettings.getAdditionalProperties())) {
      return true;
    }

    if (!StringUtils.equals(projectSettings.getServerId(), bindPanel.getSelectedStorageId())) {
      return true;
    }

    if (!StringUtils.equals(projectSettings.getProjectKey(), bindPanel.getSelectedProjectKey())) {
      return true;
    }

    if(projectSettings.rootModulesOnly() ^ bindPanel.rootModulesOnly()) {
      return true;
    }

    if(projectSettings.isBindingEnabled() ^ bindPanel.isBindingEnabled()) {
      return true;
    }

    return false;
  }

}
