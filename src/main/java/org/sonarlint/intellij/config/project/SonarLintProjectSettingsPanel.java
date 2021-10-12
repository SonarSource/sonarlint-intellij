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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JPanel;
import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;

import static java.util.Optional.ofNullable;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class SonarLintProjectSettingsPanel implements Disposable {
  private final Project project;
  private final SonarLintProjectPropertiesPanel propsPanel;
  private final JPanel root;
  private final JPanel rootBindPane;
  private final JPanel rootPropertiesPane;
  private final ProjectExclusionsPanel exclusionsPanel;
  private SonarLintProjectBindPanel bindPanel;

  public SonarLintProjectSettingsPanel(Project project) {
    this.project = project;
    bindPanel = new SonarLintProjectBindPanel();
    propsPanel = new SonarLintProjectPropertiesPanel();
    exclusionsPanel = new ProjectExclusionsPanel(project);
    root = new JPanel(new BorderLayout());
    JBTabbedPane tabs = new JBTabbedPane();

    rootBindPane = new JPanel(new BorderLayout());
    rootBindPane.add(bindPanel.create(project), BorderLayout.NORTH);

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

  public void load(List<ServerConnection> servers, SonarLintProjectSettings projectSettings, Map<Module, SonarLintModuleSettings> moduleSettings) {
    propsPanel.setAnalysisProperties(projectSettings.getAdditionalProperties());
    bindPanel.load(servers, projectSettings, moduleSettings);
    exclusionsPanel.load(projectSettings);
  }

  public void save(Project project, SonarLintProjectSettings projectSettings) throws ConfigurationException {
    String selectedProjectKey = bindPanel.getSelectedProjectKey();
    ServerConnection selectedConnection = bindPanel.getSelectedConnection();
    boolean bindingEnabled = bindPanel.isBindingEnabled();
    List<ModuleBindingPanel.ModuleBinding> moduleBindings = bindPanel.getModuleBindings();
    if (bindingEnabled) {
      if (selectedConnection == null) {
        throw new ConfigurationException("Connection should not be empty");
      }
      if (selectedProjectKey == null || selectedProjectKey.isBlank()) {
        throw new ConfigurationException("Project key should not be empty");
      }
      for (ModuleBindingPanel.ModuleBinding binding : moduleBindings) {
        String moduleProjectKey = binding.getSonarProjectKey();
        if (moduleProjectKey == null || moduleProjectKey.isBlank()) {
          throw new ConfigurationException("Project key for module '" + binding.getModule().getName() + "' should not be empty");
        }
      }
    }
    projectSettings.setAdditionalProperties(propsPanel.getProperties());
    exclusionsPanel.save(projectSettings);

    ProjectBindingManager bindingManager = getService(project, ProjectBindingManager.class);
    if (bindingEnabled) {
      Map<Module, String> moduleBindingsMap = moduleBindings
        .stream().collect(Collectors.toMap(ModuleBindingPanel.ModuleBinding::getModule, ModuleBindingPanel.ModuleBinding::getSonarProjectKey));
      bindingManager.bindTo(selectedConnection, selectedProjectKey, moduleBindingsMap);
    } else {
      bindingManager.unbind();
    }
  }

  private boolean bindingChanged(SonarLintProjectSettings projectSettings) {
    if (projectSettings.isBindingEnabled() != bindPanel.isBindingEnabled()) {
      return true;
    }

    if (bindPanel.isBindingEnabled()) {
      if (!StringUtils.equals(projectSettings.getConnectionName(), ofNullable(bindPanel.getSelectedConnection()).map(ServerConnection::getName).orElse(null))) {
        return true;
      }

      if (!StringUtils.equals(projectSettings.getProjectKey(), bindPanel.getSelectedProjectKey())) {
        return true;
      }

      List<ModuleBindingPanel.ModuleBinding> moduleBindingsFromPanel = bindPanel.getModuleBindings();
      Map<Module, SonarLintModuleSettings> moduleSettings =
        Stream.of(ModuleManager.getInstance(project).getModules())
        .collect(Collectors.toMap(m -> m, org.sonarlint.intellij.config.Settings::getSettingsFor));
      return moduleBindingsAreDifferent(moduleBindingsFromPanel, moduleSettings);
    }

    return false;
  }

  boolean moduleBindingsAreDifferent(List<ModuleBindingPanel.ModuleBinding> moduleBindingsFromPanel,
    Map<Module, SonarLintModuleSettings> settings) {
    for (ModuleBindingPanel.ModuleBinding moduleBinding : moduleBindingsFromPanel) {
      Module module = moduleBinding.getModule();
      String projectKey = moduleBinding.getSonarProjectKey();
      if (!(settings.containsKey(module) && settings.get(module).getProjectKey().equals(projectKey))) {
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
