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

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import icons.SonarLintIcons;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.tasks.ServerDownloadProjectTask;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.WEST;
import static java.util.Optional.ofNullable;

public class SonarLintProjectBindPanel {
  private static final String CONNECTION_EMPTY_TEXT = "<No connections configured>";

  private JPanel rootPanel;
  private JBCheckBox bindEnable;

  // server mgmt
  private JComboBox<ServerConnection> connectionComboBox;
  private JButton configureConnectionButton;

  // binding mgmt
  private JPanel bindPanel;
  private JBTextField projectKeyTextField;
  private JButton searchProjectButton;

  private Project project;
  private JLabel connectionListLabel;
  private JLabel projectKeyLabel;
  private ModuleBindingPanel moduleBindingPanel;

  public JPanel create(Project project) {
    this.project = project;
    rootPanel = new JPanel(new BorderLayout());
    boolean pluralizeProject = ProjectAttachProcessor.canAttachToProject() && ModuleManager.getInstance(project).getModules().length > 1;
    bindEnable = new JBCheckBox("Bind project"+ (pluralizeProject ? "s" : "") + " to SonarQube / SonarCloud", true);
    bindEnable.addItemListener(new BindItemListener());
    createBindPanel();

    rootPanel.add(bindEnable, BorderLayout.NORTH);
    rootPanel.add(bindPanel, BorderLayout.CENTER);
    moduleBindingPanel = new ModuleBindingPanel(project, () -> isBindingEnabled() ? getSelectedConnection() : null);
    rootPanel.add(moduleBindingPanel.getRootPanel(), BorderLayout.SOUTH);
    return rootPanel;
  }

  public void load(Collection<ServerConnection> connections, SonarLintProjectSettings projectSettings, Map<Module, String> moduleOverrides) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    this.bindEnable.setSelected(projectSettings.isBindingEnabled());

    connectionComboBox.removeAllItems();
    setConnectionList(connections, projectSettings.getConnectionName());
    var selectedProjectKey = projectSettings.getProjectKey();
    if (selectedProjectKey != null) {
      projectKeyTextField.setText(selectedProjectKey);
    }
    moduleBindingPanel.load(moduleOverrides);
  }

  @CheckForNull
  public ServerConnection getSelectedConnection() {
    // do things in a type safe way
    var idx = connectionComboBox.getSelectedIndex();
    if (idx < 0) {
      return null;
    }

    return connectionComboBox.getModel().getElementAt(idx);
  }

  @CheckForNull
  public String getSelectedProjectKey() {
    return projectKeyTextField.getText();
  }

  /**
   * If no connection is selected, some components items disabled.
   * Should be called when selected connection changes.
   */
  private void onConnectionSelected() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    var connectionSelected = getSelectedConnection() != null;
    projectKeyTextField.setEnabled(connectionSelected);
    projectKeyTextField.setEditable(connectionSelected);
    searchProjectButton.setEnabled(connectionSelected);
  }

  /**
   * Assumes that it's bound and a server is selected
   */
  @CheckForNull
  private Map<String, ServerProject> downloadProjectList(ServerConnection selectedConnection) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    var core = SonarLintUtils.getService(SonarLintEngineManager.class);
    var engine = core.getConnectedEngine(selectedConnection.getName());
    var downloadTask = new ServerDownloadProjectTask(project, engine, selectedConnection);

    try {
      ProgressManager.getInstance().run(downloadTask);
      return downloadTask.getResult();
    } catch (Exception e) {
      var msg = e.getMessage() != null ? e.getMessage() : "Failed to download list of projects";
      Messages.showErrorDialog(rootPanel, msg, "Error Downloading Project List");
      return null;
    }
  }

  public void connectionsChanged(List<ServerConnection> connectionList) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    // keep selection if possible
    final var selectedConnection = getSelectedConnection();
    var previousSelectedStorageId = selectedConnection != null ? selectedConnection.getName() : null;
    connectionComboBox.removeAllItems();
    setConnectionList(connectionList, previousSelectedStorageId);
  }

  /**
   * Sets new connections in the combo box, or disable it if there aren't any.
   * Will also enable or disable other components.
   */
  private void setConnectionList(Collection<ServerConnection> connections, @Nullable String previousSelectedStorageId) {
    var model = (DefaultComboBoxModel<ServerConnection>) connectionComboBox.getModel();

    if (connections.isEmpty()) {
      connectionComboBox.setEnabled(false);
      var connection = ServerConnection.newBuilder()
        .setName(CONNECTION_EMPTY_TEXT)
        .build();
      connectionComboBox.setPrototypeDisplayValue(connection);
      // ensure this is called, even when nothing is selected
    } else {
      connectionComboBox.setEnabled(bindEnable.isSelected());
      var i = 0;
      var selectedIndex = -1;
      for (var connection : connections) {
        if (previousSelectedStorageId != null && connection.getName() != null && previousSelectedStorageId.equals(connection.getName())) {
          selectedIndex = i;
        }
        connectionComboBox.setPrototypeDisplayValue(null);
        // this won't call the change listener
        model.insertElementAt(connection, i);
        i++;
      }

      // can be -1 (nothing selected)
      connectionComboBox.setSelectedIndex(selectedIndex);
    }

    // ensure this is called, even when nothing is selected
    onConnectionSelected();
  }

  private void createBindPanel() {

    bindPanel = new JPanel(new GridBagLayout());

    configureConnectionButton = new JButton();
    configureConnectionButton.setAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        actionConfigureConnections();
      }
    });
    configureConnectionButton.setText("Configure the connection...");

    connectionComboBox = new ComboBox<>();
    connectionListLabel = new JLabel("Connection:");

    connectionComboBox.setRenderer(new ServerComboBoxRenderer());
    connectionComboBox.addItemListener(new ServerItemListener());

    projectKeyLabel = new JLabel("Project key:");
    projectKeyTextField = new JBTextField();
    projectKeyTextField.getEmptyText().setText("Input SonarQube/SonarCloud project key or search one");

    searchProjectButton = new JButton();
    searchProjectButton.setAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        var selectedConnection = getSelectedConnection();
        if (selectedConnection == null) {
          return;
        }
        var projects = downloadProjectList(selectedConnection);
        if (projects != null) {
          var dialog = new SearchProjectKeyDialog(rootPanel, projectKeyTextField.getText(), projects, selectedConnection.isSonarCloud());
          if (dialog.showAndGet()) {
            projectKeyTextField.setText(dialog.getSelectedProjectKey() != null ? dialog.getSelectedProjectKey() : "");
          }
        }
      }
    });
    searchProjectButton.setText("Search in list...");

    connectionListLabel.setLabelFor(connectionComboBox);

    var insets = JBUI.insets(2, 0, 0, 0);

    bindPanel.add(connectionListLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
      WEST, NONE, insets, 0, 0));
    bindPanel.add(connectionComboBox, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0,
      WEST, HORIZONTAL, insets, 0, 0));
    bindPanel.add(configureConnectionButton, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
      WEST, NONE, insets, 0, 0));

    bindPanel.add(projectKeyLabel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
      WEST, NONE, insets, 0, 0));
    bindPanel.add(projectKeyTextField, new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0,
      WEST, HORIZONTAL, insets, 0, 0));
    bindPanel.add(searchProjectButton, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
      WEST, HORIZONTAL, insets, 0, 0));

    // Consume extra space
    bindPanel.add(new JPanel(), new GridBagConstraints(0, 2, 3, 1, 0.0, 1.0,
      WEST, HORIZONTAL, insets, 0, 0));
  }

  /**
   * Navigates to global configuration. There are 2 possible ways of doing it, depending on how the settings are opened.
   */
  private void actionConfigureConnections() {
    var allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(rootPanel));
    if (allSettings != null) {
      final var globalConfigurable = allSettings.find(SonarLintGlobalConfigurable.class);
      if (globalConfigurable != null) {
        allSettings.select(globalConfigurable);
      }
    } else {
      var globalConfigurable = new SonarLintGlobalConfigurable();
      ShowSettingsUtil.getInstance().editConfigurable(rootPanel, globalConfigurable);
    }
  }

  public boolean isBindingEnabled() {
    return bindEnable.isSelected();
  }

  public List<ModuleBindingPanel.ModuleBinding> getModuleBindings() {
    return moduleBindingPanel.getModuleBindings();
  }

  public boolean isModified(SonarLintProjectSettings projectSettings) {
    if (projectSettings.isBindingEnabled() != isBindingEnabled()) {
      return true;
    }

    if (isBindingEnabled()) {
      if (!StringUtils.equals(projectSettings.getConnectionName(), ofNullable(getSelectedConnection()).map(ServerConnection::getName).orElse(null))) {
        return true;
      }

      if (!StringUtils.equals(projectSettings.getProjectKey(), getSelectedProjectKey())) {
        return true;
      }

      return moduleBindingPanel.isModified();
    }
    return false;
  }

  /**
   * Render a connection in combo box
   */
  private class ServerComboBoxRenderer extends ColoredListCellRenderer<ServerConnection> {
    @Override
    protected void customizeCellRenderer(JList list, @Nullable ServerConnection value, int index, boolean selected, boolean hasFocus) {
      if (list.getModel().getSize() == 0) {
        if (connectionComboBox.isEnabled()) {
          append(CONNECTION_EMPTY_TEXT, SimpleTextAttributes.ERROR_ATTRIBUTES);
        } else {
          append(CONNECTION_EMPTY_TEXT, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        return;
      }

      if (value == null) {
        return;
      }

      SimpleTextAttributes attrs;
      if (connectionComboBox.isEnabled()) {
        attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      } else {
        attrs = SimpleTextAttributes.GRAYED_ATTRIBUTES;
      }

      append(value.getName(), attrs, true);
      setToolTipText("Bind project using this connection");
      if (value.isSonarCloud()) {
        setIcon(SonarLintIcons.ICON_SONARCLOUD_16);
      } else {
        setIcon(SonarLintIcons.ICON_SONARQUBE_16);
      }
    }
  }

  private class ServerItemListener implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent event) {
      if (event.getStateChange() == ItemEvent.SELECTED) {
        onConnectionSelected();
      }
    }
  }

  private class BindItemListener implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent e) {
      boolean bound = e.getStateChange() == ItemEvent.SELECTED;

      bindPanel.setEnabled(bound);
      connectionListLabel.setEnabled(bound);
      projectKeyLabel.setEnabled(bound);
      projectKeyTextField.setEnabled(bound);
      searchProjectButton.setEnabled(bound);
      connectionComboBox.setEnabled(bound);
      configureConnectionButton.setEnabled(bound);
      moduleBindingPanel.setEnabled(bound);
    }
  }
}
