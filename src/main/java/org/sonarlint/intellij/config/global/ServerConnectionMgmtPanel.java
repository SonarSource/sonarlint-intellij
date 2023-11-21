/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.global.wizard.ServerConnectionWizard;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class ServerConnectionMgmtPanel implements ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String LABEL_NO_SERVERS = "Add a connection to SonarQube or SonarCloud";

  // UI
  private JPanel panel;
  private JPanel serversPanel;
  private JBList<ServerConnection> connectionList;

  // Model
  private GlobalConfigurationListener connectionChangeListener;
  private final Map<String, ServerConnectionWithAuth> updatedConnectionsByName = new HashMap<>();
  private final Map<String, ServerConnectionWithAuth> addedConnectionsByName = new HashMap<>();
  private final Set<String> deletedServerIds = new HashSet<>();
  private CollectionListModel<ServerConnection> listModel;

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

    serversPanel = new JPanel(new BorderLayout());

    var toolbarDecorator = ToolbarDecorator.createDecorator(connectionList)
      .setEditActionName("Edit")
      .setEditAction(e -> editSelectedConnection())
      .disableUpDownActions();

    toolbarDecorator.setAddAction(new AddConnectionAction());
    toolbarDecorator.setRemoveAction(new RemoveServerAction());

    serversPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);

    var emptyLabel = new JBLabel("No connection selected", SwingConstants.CENTER);
    var emptyPanel = new JPanel(new BorderLayout());
    emptyPanel.add(emptyLabel, BorderLayout.CENTER);

    var border = IdeBorderFactory.createTitledBorder("SonarQube / SonarCloud connections");
    panel = new JPanel(new BorderLayout());
    panel.setBorder(border);
    panel.add(serversPanel);

    connectionList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(JList list, ServerConnection connection, int index, boolean selected, boolean hasFocus) {
        setIcon(connection.getProduct().getIcon());
        append(connection.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (connection.isSonarQube()) {
          append("    (" + connection.getHostUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
        }
      }
    });
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
    return !updatedConnectionsByName.isEmpty() || !addedConnectionsByName.isEmpty() || !deletedServerIds.isEmpty();
  }

  @Override
  public void save(SonarLintGlobalSettings newSettings) {
    // use background thread because of credentials save
    runOnPooledThread(() -> {
      ServerConnectionService.getInstance().updateServerConnections(newSettings, new HashSet<>(deletedServerIds), new ArrayList<>(updatedConnectionsByName.values()),
        new ArrayList<>(addedConnectionsByName.values()));
      // remove them even if a server with the same name was later added
      unbindRemovedServers();
    });

  }

  @Override
  public void load(SonarLintGlobalSettings settings) {
    updatedConnectionsByName.clear();
    addedConnectionsByName.clear();
    deletedServerIds.clear();

    listModel = new CollectionListModel<>(new ArrayList<>());
    var serverConnections = ServerConnectionService.getInstance().getConnections();

    listModel.add(serverConnections);
    connectionList.setModel(listModel);

    if (!serverConnections.isEmpty()) {
      connectionList.setSelectedValue(serverConnections.get(0), true);
    }
  }

  @Nullable
  private ServerConnection getSelectedConnection() {
    return connectionList.getSelectedValue();
  }

  List<ServerConnection> getConnections() {
    return listModel.getItems();
  }

  private void editSelectedConnection() {
    var selectedConnection = getSelectedConnection();
    int selectedIndex = connectionList.getSelectedIndex();

    if (selectedConnection != null) {
      var connectionName = selectedConnection.getName();
      runOnPooledThread(() -> {
        var previousCredentials = getCredentialsForEdition(connectionName);
        runOnUiThread(ModalityState.any(), () -> {
          var serverEditor = ServerConnectionWizard.forConnectionEdition(new ServerConnectionWithAuth(selectedConnection, previousCredentials));
          if (serverEditor.showAndGet()) {
            var editedConnectionWithAuth = serverEditor.getConnectionWithAuth();
            listModel.setElementAt(editedConnectionWithAuth.getConnection(), selectedIndex);
            if (addedConnectionsByName.containsKey(connectionName)) {
              addedConnectionsByName.put(connectionName, editedConnectionWithAuth);
            } else {
              updatedConnectionsByName.put(connectionName, editedConnectionWithAuth);
            }
            connectionChangeListener.changed(getConnections());
          }
        });
      });
    }
  }

  private ServerConnectionCredentials getCredentialsForEdition(String connectionName) {
    var connection = addedConnectionsByName.get(connectionName);
    if (connection != null) {
      return connection.getCredentials();
    }
    connection = updatedConnectionsByName.get(connectionName);
    if (connection != null) {
      return connection.getCredentials();
    }
    return ServerConnectionService.getInstance().getServerCredentialsByName(connectionName)
      .orElseThrow(() -> new IllegalStateException("Credentials for connection '" + connectionName + "' not found"));
  }

  private class AddConnectionAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      var existingNames = getConnections().stream().map(ServerConnection::getName).collect(Collectors.toSet());
      var wizard = ServerConnectionWizard.forNewConnection(existingNames);
      if (wizard.showAndGet()) {
        var created = wizard.getConnectionWithAuth();
        var connectionName = created.getConnection().getName();
        if (deletedServerIds.contains(connectionName)) {
          updatedConnectionsByName.put(connectionName, created);
          deletedServerIds.remove(connectionName);
        } else {
          addedConnectionsByName.put(connectionName, created);
        }
        listModel.add(created.getConnection());
        connectionList.setSelectedIndex(listModel.getSize() - 1);
        connectionChangeListener.changed(getConnections());
      }
    }
  }

  private class RemoveServerAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      var connection = getSelectedConnection();
      var selectedIndex = connectionList.getSelectedIndex();

      if (connection == null) {
        return;
      }

      var openProjects = ProjectManager.getInstance().getOpenProjects();
      var projectsUsingNames = getOpenProjectNames(openProjects, connection);

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

      listModel.remove(connection);
      var connectionName = connection.getName();
      if (!addedConnectionsByName.containsKey(connectionName)) {
        deletedServerIds.add(connectionName);
      }
      addedConnectionsByName.remove(connectionName);
      updatedConnectionsByName.remove(connectionName);
      connectionChangeListener.changed(getConnections());

      if (listModel.getSize() > 0) {
        var newIndex = Math.min(listModel.getSize() - 1, Math.max(selectedIndex - 1, 0));
        connectionList.setSelectedValue(listModel.getElementAt(newIndex), true);
      }
    }

    private List<String> getOpenProjectNames(Project[] openProjects, ServerConnection server) {
      return Stream.of(openProjects)
        .filter(p -> server.getName().equals(getSettingsFor(p).getConnectionName()))
        .map(Project::getName)
        .toList();
    }
  }
}
