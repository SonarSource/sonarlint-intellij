/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.tasks.ConnectionTestTask;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class SonarLintProjectSettingsPanel implements Disposable {
  static final int BINDING_TAB_INDEX = 0;

  private final SonarLintProjectPropertiesPanel propsPanel;
  private final JPanel root;
  private final JPanel rootBindPane;
  private final JPanel rootPropertiesPane;
  private final ProjectExclusionsPanel exclusionsPanel;
  private final SupportedLanguagesPanel supportedLanguagesPanel;
  private final JBTabbedPane tabs;
  private SonarLintProjectBindPanel bindPanel;

  public SonarLintProjectSettingsPanel(Project project) {
    bindPanel = new SonarLintProjectBindPanel();
    propsPanel = new SonarLintProjectPropertiesPanel();
    exclusionsPanel = new ProjectExclusionsPanel(project);
    root = new JPanel(new BorderLayout());
    tabs = new JBTabbedPane();
    supportedLanguagesPanel = new SupportedLanguagesPanel(() -> {
      tabs.setSelectedIndex(BINDING_TAB_INDEX);
      return kotlin.Unit.INSTANCE;
    });

    rootBindPane = new JPanel(new BorderLayout());
    rootBindPane.add(bindPanel.create(project), BorderLayout.NORTH);

    rootPropertiesPane = new JPanel(new BorderLayout());
    rootPropertiesPane.add(propsPanel.create(), BorderLayout.CENTER);

    tabs.insertTab("Bind to SonarQube (Server, Cloud)", null, rootBindPane, "Configure the binding to SonarQube (Server, Cloud)", 0);
    tabs.insertTab("File Exclusions", null, exclusionsPanel.getComponent(), "Configure which files to exclude from analysis", 1);
    tabs.insertTab("Analysis Properties", null, rootPropertiesPane, "Configure analysis properties", 2);
    tabs.insertTab("Supported Languages", null, supportedLanguagesPanel.getComponent(), "View supported languages", 3);

    root.add(tabs, BorderLayout.CENTER);
  }

  public JPanel getRootPane() {
    return root;
  }

  public void load(List<ServerConnection> servers, SonarLintProjectSettings projectSettings, Map<Module, String> moduleOverrides) {
    propsPanel.setAnalysisProperties(projectSettings.getAdditionalProperties());
    bindPanel.load(servers, projectSettings, moduleOverrides);
    exclusionsPanel.load(projectSettings);
    supportedLanguagesPanel.load(projectSettings);
  }

  public void save(Project project, SonarLintProjectSettings projectSettings) throws ConfigurationException {
    var selectedProjectKey = bindPanel.getSelectedProjectKey();
    var selectedConnection = bindPanel.getSelectedConnection();
    var bindingEnabled = bindPanel.isBindingEnabled();
    var moduleBindings = bindPanel.getModuleBindings();
    if (bindingEnabled) {
      if (selectedConnection == null) {
        throw new ConfigurationException("Connection should not be empty");
      }
      if (!getGlobalSettings().connectionExists(selectedConnection.getName())) {
        throw new ConfigurationException("Connection should be saved first");
      }
      isConnectionValid(project, selectedConnection);
      if (selectedProjectKey == null || selectedProjectKey.isBlank()) {
        throw new ConfigurationException("Project key should not be empty");
      }
      for (var binding : moduleBindings) {
        var moduleProjectKey = binding.getSonarProjectKey();
        if (moduleProjectKey == null || moduleProjectKey.isBlank()) {
          throw new ConfigurationException("Project key for module '" + binding.getModule().getName() + "' should not be empty");
        }
      }
    }
    projectSettings.setAdditionalProperties(propsPanel.getProperties());
    exclusionsPanel.save(projectSettings);

    var bindingManager = getService(project, ProjectBindingManager.class);
    if (bindingEnabled) {
      var moduleBindingsMap = moduleBindings
        .stream().collect(Collectors.toMap(ModuleBindingPanel.ModuleBinding::getModule, ModuleBindingPanel.ModuleBinding::getSonarProjectKey));
      bindingManager.bindToManually(selectedConnection, selectedProjectKey, moduleBindingsMap);
    } else {
      bindingManager.unbind();
    }
  }

  private static void isConnectionValid(Project project, ServerConnection connection) throws ConfigurationException {
    var connectionTest = new ConnectionTestTask(connection);
    var msg = "Failed to connect to the server. Please check the configuration.";
    ValidateConnectionResponse result;
    try {
      result = ProgressManager.getInstance().run(connectionTest);
    } catch (Exception e) {
      SonarLintConsole.get(project).error("Connection test failed", e);
      if (e.getMessage() != null) {
        msg = msg + " Error: " + e.getMessage();
      }
      throw new ConfigurationException(msg);
    }
    if (result != null && !result.isSuccess()) {
      throw new ConfigurationException(result.getMessage());
    }
  }

  public boolean areExclusionsModified(SonarLintProjectSettings projectSettings) {
    return exclusionsPanel.isModified(projectSettings);
  }

  public boolean isModified(SonarLintProjectSettings projectSettings) {
    return bindPanel.isModified(projectSettings)
      || exclusionsPanel.isModified(projectSettings)
      || propsPanel.isModified(projectSettings)
      || supportedLanguagesPanel.isModified(projectSettings);
  }

  @Override
  public void dispose() {
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
