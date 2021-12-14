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
package org.sonarlint.intellij.config.global;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import icons.SonarLintIcons;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;

import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.global.wizard.ServerConnectionWizard;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.tasks.BindingStorageUpdateTask;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.util.DateUtils;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ServerConnectionMgmtPanel implements Disposable, ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String LABEL_NO_SERVERS = "Add a connection to SonarQube or SonarCloud";

  // UI
  private JPanel panel;
  private JPanel serversPanel;
  private JBList<ServerConnection> connectionList;
  private JLabel serverStatus;
  private JButton updateServerButton;

  // Model
  private GlobalConfigurationListener connectionChangeListener;
  private final List<ServerConnection> connections = new ArrayList<>();
  private final Set<String> deletedServerIds = new HashSet<>();
  private ConnectedSonarLintEngine engine = null;

  private void create() {
    var app = ApplicationManager.getApplication();
    connectionChangeListener = app.getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC);
    connectionList = new JBList<>();
    connectionList.getEmptyText().setText(LABEL_NO_SERVERS);
    connectionList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent evt) {
        if (evt.getClickCount() == 2) {
          editSelectedConnection();
        }
      }
    });
    connectionList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        onServerSelect();
      }
    });

    serversPanel = new JPanel(new BorderLayout());

    var toolbarDecorator = ToolbarDecorator.createDecorator(connectionList)
      .setEditActionName("Edit")
      .setEditAction(e -> editSelectedConnection())
      .disableUpDownActions();

    toolbarDecorator.setAddAction(new AddConnectionAction());
    toolbarDecorator.setRemoveAction(new RemoveServerAction());

    serversPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    var splitter = new Splitter(true);
    splitter.setFirstComponent(serversPanel);
    splitter.setSecondComponent(createServerStatus());

    var emptyLabel = new JBLabel("No connection selected", SwingConstants.CENTER);
    var emptyPanel = new JPanel(new BorderLayout());
    emptyPanel.add(emptyLabel, BorderLayout.CENTER);

    var border = IdeBorderFactory.createTitledBorder("SonarQube / SonarCloud connections");
    panel = new JPanel(new BorderLayout());
    panel.setBorder(border);
    panel.add(splitter);

    connectionList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(JList list, ServerConnection server, int index, boolean selected, boolean hasFocus) {
        if (server.isSonarCloud()) {
          setIcon(SonarLintIcons.ICON_SONARCLOUD_16);
        } else {
          setIcon(SonarLintIcons.ICON_SONARQUBE_16);
        }
        append(server.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (!server.isSonarCloud()) {
          append("    (" + server.getHostUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
        }
      }
    });
  }

  private JPanel createServerStatus() {
    var serverStatusPanel = new JPanel(new GridBagLayout());

    var serverStatusLabel = new JLabel("Local update: ");
    updateServerButton = new JButton();
    serverStatus = new JLabel();

    final var link = new HyperlinkLabel("");
    link.setIcon(AllIcons.General.ContextHelp);
    link.setUseIconAsLink(true);
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final var label = new JLabel("<html>Click to fetch data from the selected connection, such as the list of projects,<br>"
          + " rules, quality profiles, etc. This needs to be done before being able to select a project.</html>");
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.getInformationColor());
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getSouthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });

    var flow1 = new JPanel(new FlowLayout(FlowLayout.LEADING));
    flow1.add(serverStatusLabel);
    flow1.add(serverStatus);

    var flow2 = new JPanel(new FlowLayout(FlowLayout.LEADING));
    flow2.add(updateServerButton);
    flow2.add(link);

    serverStatusPanel.add(flow1, new GridBagConstraints(0, 0, 1, 1, 0.5, 1, GridBagConstraints.LINE_START, 0, JBUI.insets(0, 0, 0, 0), 0, 0));
    serverStatusPanel.add(flow2, new GridBagConstraints(1, 0, 1, 1, 0.5, 1, GridBagConstraints.LINE_START, 0, JBUI.insets(0, 0, 0, 0), 0, 0));

    updateServerButton.setAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        actionUpdateConnectionStorageTask();
      }
    });
    updateServerButton.setText("Update binding");
    updateServerButton.setToolTipText("Update local data: quality profile, settings, ...");

    var alignedPanel = new JPanel(new BorderLayout());
    alignedPanel.add(serverStatusPanel, BorderLayout.NORTH);
    return alignedPanel;
  }

  private void unbindRemovedServers() {
    if (deletedServerIds.isEmpty()) {
      return;
    }

    var openProjects = ProjectManager.getInstance().getOpenProjects();

    for (var openProject : openProjects) {
      var projectSettings = getSettingsFor(openProject);
      if (projectSettings.getConnectionName() != null && deletedServerIds.contains(projectSettings.getConnectionName())) {
        getService(openProject, ProjectBindingManager.class).unbind();
      }
    }
  }

  @Override
  public JComponent getComponent() {
    if (panel == null) {
      create();
    }
    return panel;
  }

  @Override
  public boolean isModified(SonarLintGlobalSettings settings) {
    return !connections.equals(settings.getServerConnections());
  }

  @Override
  public void save(SonarLintGlobalSettings settings) {
    var copyList = new ArrayList<>(connections);
    settings.setServerConnections(copyList);

    // remove them even if a server with the same name was later added
    unbindRemovedServers();
  }

  @Override
  public void load(SonarLintGlobalSettings settings) {
    connections.clear();
    deletedServerIds.clear();

    var listModel = new CollectionListModel<ServerConnection>(new ArrayList<>());
    listModel.add(settings.getServerConnections());
    connections.addAll(settings.getServerConnections());
    connectionList.setModel(listModel);

    if (!connections.isEmpty()) {
      connectionList.setSelectedValue(connections.get(0), true);
    }
  }

  @Nullable
  private ServerConnection getSelectedConnection() {
    return connectionList.getSelectedValue();
  }

  List<ServerConnection> getConnections() {
    return connections;
  }

  private void onServerSelect() {
    switchTo(getSelectedConnection());
  }

  private void switchTo(@Nullable ServerConnection server) {
    engine = null;

    if (server != null) {
      var serverManager = getService(SonarLintEngineManager.class);
      // Initial loading of the connected engine can be long, sent to pooled thread
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        engine = serverManager.getConnectedEngine(server.getName());
        updateBindingStatusLabelAsync();
      });
    } else {
      serverStatus.setText("[ no connection selected ]");
      updateServerButton.setEnabled(false);
    }
  }

  private void updateBindingStatusLabelAsync() {
    if (engine == null) {
      serverStatus.setText("checking...");
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      var statusText = getStatusText();
      ApplicationManager.getApplication().invokeLater(() -> {
        serverStatus.setText(statusText);
        // Using ModalityState#any() since we are only updating light UI stuff
      }, ModalityState.any());
    });
  }

  private String getStatusText() {
    var storageStatus = engine.getGlobalStorageStatus();
    if (storageStatus == null) {
      return "need sync (empty)";
    }
    if (storageStatus.isStale()) {
      return "need sync (outdated)";
    }
    return DateUtils.toAge(storageStatus.getLastUpdateDate().getTime());
  }

  private void actionUpdateConnectionStorageTask() {
    var connection = getSelectedConnection();
    if (connection == null || engine == null) {
      return;
    }

    updateConnectionStorage(connection, engine, false);
  }

  public static void updateConnectionStorage(ServerConnection connection, ConnectedSonarLintEngine engine, boolean onlyProjects) {
    var task = new BindingStorageUpdateTask(engine, connection, !onlyProjects, true, null);
    ProgressManager.getInstance().run(task.asBackground());
  }

  private void editSelectedConnection() {
    var selectedConnection = getSelectedConnection();
    int selectedIndex = connectionList.getSelectedIndex();

    if (selectedConnection != null) {
      var serverEditor = ServerConnectionWizard.forConnectionEdition(selectedConnection);
      if (serverEditor.showAndGet()) {
        var editedConnection = serverEditor.getConnection();
        ((CollectionListModel<ServerConnection>) connectionList.getModel()).setElementAt(editedConnection, selectedIndex);
        connections.set(connections.indexOf(selectedConnection), editedConnection);
        connectionChangeListener.changed(connections);
        updateConnectionStorage(editedConnection);
      }
    }
  }

  @Override
  public void dispose() {
    engine = null;
  }

  private class AddConnectionAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      var existingNames = connections.stream().map(ServerConnection::getName).collect(Collectors.toSet());
      var wizard = ServerConnectionWizard.forNewConnection(existingNames);
      if (wizard.showAndGet()) {
        var created = wizard.getConnection();
        connections.add(created);
        ((CollectionListModel<ServerConnection>) connectionList.getModel()).add(created);
        connectionList.setSelectedIndex(connectionList.getModel().getSize() - 1);
        connectionChangeListener.changed(connections);
        updateConnectionStorage(created);
      }
    }
  }

  private static void updateConnectionStorage(ServerConnection toUpdate) {
    var serverManager = getService(SonarLintEngineManager.class);
    var task = new BindingStorageUpdateTask(serverManager.getConnectedEngine(toUpdate.getName()), toUpdate, true, false, null);
    ProgressManager.getInstance().run(task.asBackground());
  }

  private class RemoveServerAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      var server = getSelectedConnection();
      var selectedIndex = connectionList.getSelectedIndex();

      if (server == null) {
        return;
      }

      var openProjects = ProjectManager.getInstance().getOpenProjects();
      var projectsUsingNames = getOpenProjectNames(openProjects, server);

      if (!projectsUsingNames.isEmpty()) {
        var projects = String.join("<br>", projectsUsingNames);
        var response = Messages.showYesNoDialog(serversPanel,
          "<html>The following opened projects are bound to this connection:<br><b>" +
            projects + "</b><br>Delete the connection?</html>",
          "Connection In Use", Messages.getWarningIcon());
        if (response == Messages.NO) {
          return;
        }
      }

      var model = (CollectionListModel<ServerConnection>) connectionList.getModel();
      // it's not removed from serverIds and editorList
      model.remove(server);
      connections.remove(server);
      connectionChangeListener.changed(connections);

      if (model.getSize() > 0) {
        var newIndex = Math.min(model.getSize() - 1, Math.max(selectedIndex - 1, 0));
        connectionList.setSelectedValue(model.getElementAt(newIndex), true);
      }
    }

    private List<String> getOpenProjectNames(Project[] openProjects, ServerConnection server) {
      return Stream.of(openProjects)
        .filter(p -> server.getName().equals(getSettingsFor(p).getConnectionName()))
        .map(Project::getName)
        .collect(Collectors.toList());
    }
  }
}
