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

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBInsets;
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
import javax.swing.border.Border;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.tasks.ServerDownloadProjectTask;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteProject;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.WEST;

public class SonarLintProjectBindPanel {
  private static final String SERVER_EMPTY_TEXT = "<No connections configured>";

  private JPanel rootPanel;
  private JBCheckBox bindEnable;

  // server mgmt
  private JComboBox<SonarQubeServer> serverComboBox;
  private JButton configureServerButton;

  // binding mgmt
  private JPanel bindPanel;
  private JBTextField projectKeyTextField;
  private JButton searchProjectButton;

  private Project project;

  public JPanel create(Project project) {
    this.project = project;
    rootPanel = new JPanel(new BorderLayout());
    bindEnable = new JBCheckBox("Bind project to SonarQube / SonarCloud", true);
    bindEnable.addItemListener(new BindItemListener());
    createBindPanel();

    rootPanel.add(bindEnable, BorderLayout.NORTH);
    rootPanel.add(bindPanel, BorderLayout.CENTER);
    return rootPanel;
  }

  public void load(Collection<SonarQubeServer> servers, boolean enabled, @Nullable String selectedServerId, @Nullable String selectedProjectKey) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    this.bindEnable.setSelected(enabled);

    serverComboBox.removeAllItems();
    setServerList(servers, selectedServerId);
    if (selectedProjectKey != null) {
      projectKeyTextField.setText(selectedProjectKey);
    }
  }

  @CheckForNull
  public String getSelectedStorageId() {
    SonarQubeServer server = getSelectedServer();
    return server != null ? server.getName() : null;
  }

  @CheckForNull
  private SonarQubeServer getSelectedServer() {
    // do things in a type safe way
    int idx = serverComboBox.getSelectedIndex();
    if (idx < 0) {
      return null;
    }

    return serverComboBox.getModel().getElementAt(idx);
  }

  @CheckForNull
  public String getSelectedProjectKey() {
    return projectKeyTextField.getText();
  }

  /**
   * If no server is selected, some components items disabled.
   * Should be called when selected Server changes.
   */
  private void onServerSelected() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean serverSelected = getSelectedStorageId() != null;
    projectKeyTextField.setEnabled(serverSelected);
    projectKeyTextField.setEditable(serverSelected);
    searchProjectButton.setEnabled(serverSelected);
  }

  /**
   * Assumes that it's bound and a server is selected
   */
  @CheckForNull
  private Map<String, RemoteProject> downloadProjectList() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    SonarQubeServer selectedServer = getSelectedServer();
    String storageId = getSelectedStorageId();
    if (selectedServer == null || storageId == null) {
      return null;
    }

    SonarLintEngineManager core = SonarLintUtils.get(SonarLintEngineManager.class);
    ConnectedSonarLintEngine engine = core.getConnectedEngine(storageId);
    ServerDownloadProjectTask downloadTask = new ServerDownloadProjectTask(project, engine, selectedServer);

    try {
      ProgressManager.getInstance().run(downloadTask);
      return downloadTask.getResult();
    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : "Failed to download list of projects";
      Messages.showErrorDialog(rootPanel, msg, "Error Downloading Project List");
      return null;
    }
  }

  public void serversChanged(List<SonarQubeServer> serverList) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    // keep selection if possible
    String previousSelectedStorageId = getSelectedStorageId();
    serverComboBox.removeAllItems();
    setServerList(serverList, previousSelectedStorageId);
  }

  /**
   * Sets new servers in the combo box, or disable it if there aren't any.
   * Will also enable or disable other components.
   */
  private void setServerList(Collection<SonarQubeServer> servers, @Nullable String previousSelectedStorageId) {
    DefaultComboBoxModel<SonarQubeServer> model = (DefaultComboBoxModel<SonarQubeServer>) serverComboBox.getModel();

    if (servers.isEmpty()) {
      serverComboBox.setEnabled(false);
      SonarQubeServer s = SonarQubeServer.newBuilder()
        .setName(SERVER_EMPTY_TEXT)
        .build();
      serverComboBox.setPrototypeDisplayValue(s);
      // ensure this is called, even when nothing is selected
    } else {
      serverComboBox.setEnabled(bindEnable.isSelected());
      int i = 0;
      int selectedIndex = -1;
      for (SonarQubeServer s : servers) {
        if (previousSelectedStorageId != null && s.getName() != null && previousSelectedStorageId.equals(s.getName())) {
          selectedIndex = i;
        }
        serverComboBox.setPrototypeDisplayValue(null);
        // this won't call the change listener
        model.insertElementAt(s, i);
        i++;
      }

      // can be -1 (nothing selected)
      serverComboBox.setSelectedIndex(selectedIndex);
    }

    // ensure this is called, even when nothing is selected
    onServerSelected();
  }

  private void createBindPanel() {
    Border b = IdeBorderFactory.createTitledBorder("Project binding");

    bindPanel = new JPanel(new GridBagLayout());
    bindPanel.setBorder(b);

    configureServerButton = new JButton();
    configureServerButton.setAction(new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        actionConfigureServers();
      }
    });
    configureServerButton.setText("Configure the connection...");

    serverComboBox = new ComboBox<>();
    JLabel serverListLabel = new JLabel("Connection:");

    serverComboBox.setRenderer(new ServerComboBoxRenderer());
    serverComboBox.addItemListener(new ServerItemListener());

    JLabel projectListLabel = new JLabel("Project:");
    projectKeyTextField = new JBTextField();
    projectKeyTextField.getEmptyText().setText("Input project key or search one");

    searchProjectButton = new JButton();
    searchProjectButton.setAction(new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        Map<String, RemoteProject> map = downloadProjectList();
        if (map != null) {
          SearchProjectKeyDialog dialog = new SearchProjectKeyDialog(rootPanel, projectKeyTextField.getText(), map);
          if (dialog.showAndGet()) {
            projectKeyTextField.setText(dialog.getSelectedProjectKey() != null ? dialog.getSelectedProjectKey() : "");
          }
        }
      }
    });
    searchProjectButton.setText("Search in list...");

    serverListLabel.setLabelFor(serverComboBox);

    JBInsets insets = JBUI.insets(2, 0, 0, 0);

    bindPanel.add(serverListLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
      WEST, NONE, insets, 0, 0));
    bindPanel.add(serverComboBox, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0,
      WEST, HORIZONTAL, insets, 0, 0));
    bindPanel.add(configureServerButton, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0,
      WEST, NONE, insets, 0, 0));

    bindPanel.add(projectListLabel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0,
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
  private void actionConfigureServers() {
    Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(rootPanel));
    if (allSettings != null) {
      final SonarLintGlobalConfigurable globalConfigurable = allSettings.find(SonarLintGlobalConfigurable.class);
      if (globalConfigurable != null) {
        allSettings.select(globalConfigurable);
      }
    } else {
      SonarLintGlobalConfigurable globalConfigurable = new SonarLintGlobalConfigurable();
      ShowSettingsUtil.getInstance().editConfigurable(rootPanel, globalConfigurable);
    }
  }

  public boolean isBindingEnabled() {
    return bindEnable.isSelected();
  }

  /**
   * Render SonarQube server in combo box
   */
  private class ServerComboBoxRenderer extends ColoredListCellRenderer<SonarQubeServer> {
    @Override protected void customizeCellRenderer(JList list, @Nullable SonarQubeServer value, int index, boolean selected, boolean hasFocus) {
      if (list.getModel().getSize() == 0) {
        if (serverComboBox.isEnabled()) {
          append(SERVER_EMPTY_TEXT, SimpleTextAttributes.ERROR_ATTRIBUTES);
        } else {
          append(SERVER_EMPTY_TEXT, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        return;
      }

      if (value == null) {
        return;
      }

      SimpleTextAttributes attrs;
      if (serverComboBox.isEnabled()) {
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
        onServerSelected();
      }
    }
  }

  private class BindItemListener implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent e) {
      boolean bound = e.getStateChange() == ItemEvent.SELECTED;

      bindPanel.setEnabled(bound);
      serverComboBox.setEnabled(bound);
      configureServerButton.setEnabled(bound);
    }
  }
}
