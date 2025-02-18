/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.global.wizard.ServerConnectionWizard;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.CONNECTED_MODE_DOCS;

public class ServerConnectionMgmtPanel implements ConfigurationPanel<SonarLintGlobalSettings> {

  // UI
  private JPanel panel;
  private JPanel serversPanel;
  private JBList<ServerConnection> connectionList;

  // Model
  private GlobalConfigurationListener connectionChangeListener;
  private final List<ServerConnection> connections = new ArrayList<>();
  private final Set<String> deletedServerIds = new HashSet<>();

  private void create() {
    var app = ApplicationManager.getApplication();
    connectionChangeListener = app.getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC);
    connectionList = new JBList<>();
    connectionList.getEmptyText().appendLine("Add a connection to SonarQube (Server, Cloud)");
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

    var serversPanelToolbar = toolbarDecorator.createPanel();
    serversPanelToolbar.setMinimumSize(new Dimension(200, 200));
    serversPanelToolbar.setPreferredSize(new Dimension(200, 200));
    serversPanel.add(serversPanelToolbar, BorderLayout.CENTER);

    var emptyLabel = new JBLabel("No connection selected", SwingConstants.CENTER);
    var emptyPanel = new JPanel(new BorderLayout());
    emptyPanel.add(emptyLabel, BorderLayout.CENTER);

    var titlePanel = initConnectionTitle();

    var optionsPanel = new JPanel(new VerticalFlowLayout());
    var connectedModeDescription = initConnectedModeDescription();
    optionsPanel.add(connectedModeDescription);

    panel = new JPanel(new VerticalFlowLayout());
    panel.add(new SeparatorComponent(5, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), null));
    panel.add(titlePanel);
    panel.add(optionsPanel);
    panel.add(serversPanel);

    connectionList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(JList list, ServerConnection server, int index, boolean selected, boolean hasFocus) {
        if (server.isSonarCloud()) {
          String serverRegion = server.getRegion() == null ? "EU" : server.getRegion();

          setIcon(SonarLintIcons.ICON_SONARQUBE_CLOUD_16);
          if (hasMoreThanOneSCConnections() && SonarLintUtils.isDogfoodEnvironment()) {
            append("[" + serverRegion + "] " + server.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          } else {
            append(server.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        } else {
          setIcon(SonarLintIcons.ICON_SONARQUBE_SERVER_16);
          append(server.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }

        if (!server.isSonarCloud()) {
          append("    (" + server.getHostUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
        }
      }
    });
  }

  private boolean hasMoreThanOneSCConnections() {
    ListModel<ServerConnection> model = connectionList.getModel();
    long count = 0;
    for (int i = 0; i < model.getSize(); i++) {
      if (model.getElementAt(i).isSonarCloud()) {
        count++;
      }
      if (count > 1) {
        return true;
      }
    }
    return false;
  }

  private static JPanel initConnectionTitle() {
    var titlePanel = new JPanel(new HorizontalLayout(5));
    var connectionLabel = new JBLabel("Connections to SonarQube (");
    connectionLabel.setFont(connectionLabel.getFont().deriveFont(Font.BOLD, 16f));
    var sonarQubeIcon = new JBLabel(SonarLintIcons.ICON_SONARQUBE_SERVER_16);
    var sonarQubeLabel = new JBLabel("Server, ");
    sonarQubeLabel.setFont(sonarQubeLabel.getFont().deriveFont(Font.BOLD, 16f));
    var sonarCloudIcon = new JBLabel(SonarLintIcons.ICON_SONARQUBE_CLOUD_16);
    var sonarCloudLabel = new JBLabel("Cloud)");
    sonarCloudLabel.setFont(sonarQubeLabel.getFont().deriveFont(Font.BOLD, 16f));
    titlePanel.add(connectionLabel);
    titlePanel.add(sonarQubeIcon);
    titlePanel.add(sonarQubeLabel);
    titlePanel.add(sonarCloudIcon);
    titlePanel.add(sonarCloudLabel);
    return titlePanel;
  }

  private JEditorPane initConnectedModeDescription() {
    var connectedModeLabel = new JEditorPane();
    connectedModeLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        CONNECTED_MODE_DOCS.browseWithTelemetry();
      }
    });
    connectedModeLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
    initHtmlPane(connectedModeLabel);
    SwingHelper.setHtml(connectedModeLabel, "<a href=\"" + CONNECTED_MODE_DOCS.getUrl() + "\">Connected Mode</a> " +
      "links SonarQube for IDE to SonarQube (Server, Cloud) to apply the same Clean Code standards as your team. " +
        "Analyze more languages, detect more issues, receive notifications about the quality gate status, and more. " +
        "Quality Profiles and file exclusion settings defined on the server are shared between all connected users.",
      JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    return connectedModeLabel;
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
  public void save(SonarLintGlobalSettings newSettings) {
    var newConnections = new ArrayList<>(connections);
    newSettings.setServerConnections(newConnections);

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
      }
    }
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
      }
    }
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
        .toList();
    }
  }
}
