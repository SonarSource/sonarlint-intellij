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
package org.sonarlint.intellij.config.project;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.CheckForNull;
import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.Border;

import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.core.ServerUpdateTask;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.util.ResourceLoader;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;

import static org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;

public class SonarLintProjectBindPanel implements Disposable {
  private static final Logger LOGGER = Logger.getInstance(SonarLintProjectBindPanel.class);
  private static final String SERVER_EMPTY_TEXT = "<No servers configured>";
  private static final String PROJECT_NO_SERVER = "<No server selected>";
  private static final String PROJECT_EMPTY = "<No projects to display>";
  private static final String PROJECT_NO_LOCAL_CONFIG = "<Update server binding first>";

  private JPanel rootPanel;
  private JBCheckBox bindEnable;

  // server mgmt
  private JComboBox<SonarQubeServer> serverComboBox;
  private StateListener serverStateListener;
  private JButton configureServerButton;
  private ConnectedSonarLintEngine engine;

  // binding mgmt
  private JPanel bindPanel;
  private JComboBox<RemoteModule> projectComboBox;
  private String lastSelectedProjectKey;

  public JPanel create() {
    rootPanel = new JPanel(new GridBagLayout());
    bindEnable = new JBCheckBox("Enable binding to remote SonarQube server", true);
    bindEnable.addItemListener(new BindItemListener());
    createBindPanel();

    rootPanel.add(bindEnable, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    rootPanel.add(bindPanel, new GridBagConstraints(0, 1, 1, 1, 1, 1, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL, new Insets(4, 4, 4, 0), 0, 0));

    return rootPanel;
  }

  public void load(Collection<SonarQubeServer> servers, boolean enabled, @Nullable String selectedServerId, @Nullable String selectedProjectKey) {
    this.bindEnable.setSelected(enabled);
    this.lastSelectedProjectKey = selectedProjectKey;

    serverComboBox.removeAllItems();
    setServerList(servers, selectedServerId);
  }

  @CheckForNull
  public String getSelectedStorageId() {
    // do things in a type safe way
    int idx = serverComboBox.getSelectedIndex();
    if (idx < 0) {
      return null;
    }

    SonarQubeServer server = serverComboBox.getModel().getElementAt(idx);
    return server.getName();
  }

  @CheckForNull
  public String getSelectedProjectKey() {
    // do things in a type safe way
    int idx = projectComboBox.getSelectedIndex();
    if (idx < 0) {
      return null;
    } else {
      RemoteModule module = projectComboBox.getModel().getElementAt(idx);
      return module.getKey();
    }
  }

  /**
   * Updates server status label. If no server is selected, some components get disabled.
   * Should be called when selected Server changes.
   */
  private void onServerSelected() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    String selectedStorageId = getSelectedStorageId();
    SonarLintEngineManager core = SonarLintUtils.get(SonarLintEngineManager.class);

    if (engine != null) {
      engine.removeStateListener(serverStateListener);
    }

    if (selectedStorageId == null) {
      engine = null;
      serverStateListener = null;
    } else {
      // assuming that server ID is valid!
      engine = core.getConnectedEngine(selectedStorageId);
      serverStateListener = new ServerStateListener();
      engine.addStateListener(serverStateListener);
    }
    setProjects();
  }

  private void setProjects() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    DefaultComboBoxModel<RemoteModule> model = (DefaultComboBoxModel) projectComboBox.getModel();
    projectComboBox.removeAllItems();

    if (engine != null && engine.getState() == State.UPDATED) {
      Map<String, RemoteModule> moduleMap = engine.allModulesByKey();
      if (!moduleMap.isEmpty()) {
        Set<RemoteModule> orderedSet = new TreeSet<>((o1, o2) -> {
          int c1 = o1.getName().compareTo(o2.getName());
          if (c1 != 0) {
            return c1;
          }

          return o1.getKey().compareTo(o2.getKey());
        });
        orderedSet.addAll(moduleMap.values());

        RemoteModule selected = null;
        int i = 0;
        for (RemoteModule mod : orderedSet) {
          if (!mod.isRoot()) {
            continue;
          }
          // this won't call the change listener
          model.insertElementAt(mod, i);
          i++;
          if (lastSelectedProjectKey != null && lastSelectedProjectKey.equals(mod.getKey())) {
            selected = mod;
          }
        }

        if (selected != null) {
          projectComboBox.setSelectedItem(selected);
        } else if (projectComboBox.getItemCount() > 0 && lastSelectedProjectKey == null) {
          projectComboBox.setSelectedIndex(0);
        } else {
          projectComboBox.setSelectedItem(null);
        }

        projectComboBox.setPrototypeDisplayValue(null);
        projectComboBox.setEnabled(bindEnable.isSelected());
      }
    }

    // it can be happen because server has no projects, no server selected, or server not updated
    if (projectComboBox.getItemCount() == 0) {
      final String fMsg = getProjectEmptyText();
      RemoteModule empty = new RemoteModule() {
        @Override public String getKey() {
          return "";
        }

        @Override public String getName() {
          return fMsg;
        }

        @Override public boolean isRoot() {
          return true;
        }
      };

      projectComboBox.setPrototypeDisplayValue(empty);
      projectComboBox.setEnabled(false);
    }
  }

  public void serversChanged(List<SonarQubeServer> serverList) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    // keep selection if possible
    String previousSelectedStorageId = getSelectedStorageId();
    serverComboBox.removeAllItems();
    setServerList(serverList, previousSelectedStorageId);
  }

  private String getProjectEmptyText() {
    // a server is selected
    if (getSelectedStorageId() != null) {
      if (engine.getState() != State.UPDATED) {
        // not updated
        return PROJECT_NO_LOCAL_CONFIG;
      } else {
        // there are no projects in the server
        return PROJECT_EMPTY;
      }
    } else {
      // no server selected
      return PROJECT_NO_SERVER;
    }
  }

  /**
   * Sets new servers in the combo box, or disable it if there aren't any.
   * Will also enable or disable other components.
   */
  private void setServerList(Collection<SonarQubeServer> servers, @Nullable String previousSelectedStorageId) {
    DefaultComboBoxModel<SonarQubeServer> model = (DefaultComboBoxModel<SonarQubeServer>) serverComboBox.getModel();

    if (servers.isEmpty()) {
      serverComboBox.setEnabled(false);
      SonarQubeServer s = new SonarQubeServer();
      s.setName(SERVER_EMPTY_TEXT);
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

    JLabel projectListLabel = new JLabel("SonarQube project:");
    projectComboBox = new ComboBoxWithWidePopup(new DefaultComboBoxModel<RemoteModule>());
    projectComboBox.setRenderer(new ProjectComboBoxRenderer());
    projectComboBox.setEditable(false);
    projectComboBox.addItemListener(new ProjectItemListener());

    JPanel serverPanel = new JPanel(new GridLayoutManager(1, 3));
    configureServerButton = new JButton();
    serverComboBox = new ComboBox();
    JLabel serverListLabel = new JLabel("Bind to server:");

    serverComboBox.setRenderer(new ServerComboBoxRenderer());
    serverComboBox.addItemListener(new ServerItemListener());

    serverListLabel.setLabelFor(serverComboBox);

    serverPanel.add(serverComboBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    serverPanel.add(configureServerButton, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    configureServerButton.setAction(new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        actionConfigureServers();
      }
    });
    configureServerButton.setText("Configure servers...");

    bindPanel.add(serverListLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0));
    bindPanel.add(serverPanel, new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, new Insets(2, 3, 0, 0), 0, 0));
    bindPanel.add(projectListLabel, new GridBagConstraints(0, 1, 1, 1, 0, 1, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0));
    bindPanel.add(projectComboBox, new GridBagConstraints(1, 1, 1, 1, 1, 1, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, new Insets(2, 3, 0, 0), 0, 0));

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

  public void actionUpdateProjectTask() {
    if (engine == null) {
      // should mean that no server is selected
      return;
    }
    // do things in a type safe way
    int idx = serverComboBox.getSelectedIndex();
    if (idx < 0) {
      return;
    }

    SonarQubeServer server = serverComboBox.getModel().getElementAt(idx);
    String projectKey = getSelectedProjectKey();
    ServerUpdateTask task = new ServerUpdateTask(engine, server, Collections.singleton(projectKey), true);
    ProgressManager.getInstance().run(task.asBackground());
  }

  public boolean isBindingEnabled() {
    return bindEnable.isSelected();
  }

  @Override public void dispose() {
    if (serverStateListener != null) {
      engine.removeStateListener(serverStateListener);
    }
  }

  /**
   * Render SonarQube server in combo box
   */
  private class ServerComboBoxRenderer extends ColoredListCellRenderer<SonarQubeServer> {
    @Override protected void customizeCellRenderer(JList list, SonarQubeServer value, int index, boolean selected, boolean hasFocus) {
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
      setToolTipText("Connect to this SonarQube server");
      try {
        setIcon(ResourceLoader.getIcon(ResourceLoader.ICON_SONARQUBE_16));
      } catch (IOException e) {
        LOGGER.error("Failed to load SonarQube icon", e);
      }
    }
  }

  /**
   * Render modules in combo box
   */
  private class ProjectComboBoxRenderer extends ColoredListCellRenderer<RemoteModule> {
    @Override protected void customizeCellRenderer(JList list, RemoteModule value, int index, boolean selected, boolean hasFocus) {
      if (list.getModel().getSize() == 0) {
        if (bindEnable.isSelected()) {
          append(getProjectEmptyText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
        } else {
          append(getProjectEmptyText(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        return;
      }

      if (value == null) {
        return;
      }

      SimpleTextAttributes attrs;
      if (projectComboBox.isEnabled()) {
        attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      } else {
        attrs = SimpleTextAttributes.GRAYED_ATTRIBUTES;
      }
      append(value.getName(), attrs, true);
      // it is not working: appendTextPadding
      append(" ");
      if (index >= 0) {
        append("(" + value.getKey() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
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
      boolean updated = engine != null && engine.getState() == State.UPDATED;

      bindPanel.setEnabled(bound);
      serverComboBox.setEnabled(bound);
      configureServerButton.setEnabled(bound);

      projectComboBox.setEnabled(bound && updated);

      setProjects();
    }
  }

  private class ProjectItemListener implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent event) {
      if (event.getStateChange() == ItemEvent.SELECTED) {
        lastSelectedProjectKey = getSelectedProjectKey();
      }
    }
  }

  private class ServerStateListener implements StateListener {
    @Override public void stateChanged(State newState) {
      // invoke in EDT
      ApplicationManager.getApplication().invokeLater(() -> {
        if (engine == null || engine.getState() == State.UPDATED) {
          setProjects();
        }
      });
    }
  }
}
