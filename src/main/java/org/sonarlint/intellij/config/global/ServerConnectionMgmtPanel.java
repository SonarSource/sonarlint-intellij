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
package org.sonarlint.intellij.config.global;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.global.wizard.ServerConnectionWizard;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.tasks.ConnectionUpdateTask;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.util.DateUtils;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ServerConnectionMgmtPanel implements Disposable, ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String LABEL_NO_SERVERS = "Add a connection to SonarQube or SonarCloud";

  // UI
  private JPanel panel;
  private Splitter splitter;
  private JPanel serversPanel;
  private JBList<ServerConnection> connectionList;
  private JPanel emptyPanel;
  private JLabel serverStatus;
  private JButton updateServerButton;

  // Model
  private GlobalConfigurationListener connectionChangeListener;
  private final List<ServerConnection> connections = new ArrayList<>();
  private final Set<String> deletedServerIds = new HashSet<>();
  private ConnectedSonarLintEngine engine = null;
  private StateListener engineListener;

  private void create() {
    Application app = ApplicationManager.getApplication();
    connectionChangeListener = app.getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC);
    connectionList = new JBList<>();
    connectionList.getEmptyText().setText(LABEL_NO_SERVERS);
    connectionList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent evt) {
        if (evt.getClickCount() == 2) {
          editSelectedConnection(false);
        }
      }
    });
    connectionList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        onServerSelect();
      }
    });

    serversPanel = new JPanel(new BorderLayout());

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(connectionList)
      .setEditActionName("Edit")
      .setEditAction(e -> editSelectedConnection(false))
      .disableUpDownActions();

    toolbarDecorator.setAddAction(new AddServerAction());
    toolbarDecorator.setRemoveAction(new RemoveServerAction());

    serversPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    splitter = new Splitter(true);
    splitter.setFirstComponent(serversPanel);
    splitter.setSecondComponent(createServerStatus());

    JBLabel emptyLabel = new JBLabel("No connection selected", SwingConstants.CENTER);
    emptyPanel = new JPanel(new BorderLayout());
    emptyPanel.add(emptyLabel, BorderLayout.CENTER);

    Border b = IdeBorderFactory.createTitledBorder("SonarQube / SonarCloud connections");
    panel = new JPanel(new BorderLayout());
    panel.setBorder(b);
    panel.add(splitter);

    connectionList.setCellRenderer(new ColoredListCellRenderer<ServerConnection>() {
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
    JPanel serverStatusPanel = new JPanel(new GridBagLayout());

    JLabel serverStatusLabel = new JLabel("Local update: ");
    updateServerButton = new JButton();
    serverStatus = new JLabel();

    final HyperlinkLabel link = new HyperlinkLabel("");
    link.setIcon(AllIcons.General.ContextHelp);
    link.setUseIconAsLink(true);
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final JLabel label = new JLabel("<html>Click to fetch data from the selected connection, such as the list of projects,<br>"
          + " rules, quality profiles, etc. This needs to be done before being able to select a project.");
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.getInformationColor());
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getSouthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });

    JPanel flow1 = new JPanel(new FlowLayout(FlowLayout.LEADING));
    flow1.add(serverStatusLabel);
    flow1.add(serverStatus);

    JPanel flow2 = new JPanel(new FlowLayout(FlowLayout.LEADING));
    flow2.add(updateServerButton);
    flow2.add(link);

    serverStatusPanel.add(flow1, new GridBagConstraints(0, 0, 1, 1, 0.5, 1, GridBagConstraints.LINE_START, 0, JBUI.insets(0, 0, 0, 0), 0, 0));
    serverStatusPanel.add(flow2, new GridBagConstraints(1, 0, 1, 1, 0.5, 1, GridBagConstraints.LINE_START, 0, JBUI.insets(0, 0, 0, 0), 0, 0));

    updateServerButton.setAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        actionUpdateServerTask();
      }
    });
    updateServerButton.setText("Update binding");
    updateServerButton.setToolTipText("Update local data: quality profile, settings, ...");

    JPanel alignedPanel = new JPanel(new BorderLayout());
    alignedPanel.add(serverStatusPanel, BorderLayout.NORTH);
    return alignedPanel;
  }

  private void unbindRemovedServers() {
    if (deletedServerIds.isEmpty()) {
      return;
    }

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

    for (Project openProject : openProjects) {
      SonarLintProjectSettings projectSettings = getSettingsFor(openProject);
      if (projectSettings.getConnectionName() != null && deletedServerIds.contains(projectSettings.getConnectionName())) {
        projectSettings.setBindingEnabled(false);
        projectSettings.setConnectionName(null);
        projectSettings.setProjectKey(null);
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
    List<ServerConnection> copyList = new ArrayList<>(connections);
    settings.setServerConnections(copyList);

    //remove them even if a server with the same name was later added
    unbindRemovedServers();
  }

  @Override
  public void load(SonarLintGlobalSettings settings) {
    connections.clear();
    deletedServerIds.clear();

    CollectionListModel<ServerConnection> listModel = new CollectionListModel<>(new ArrayList<>());
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
    if (engineListener != null) {
      engine.removeStateListener(engineListener);
      engineListener = null;
      engine = null;
    }

    if (server != null) {
      SonarLintEngineManager serverManager = SonarLintUtils.getService(SonarLintEngineManager.class);
      engine = serverManager.getConnectedEngine(server.getName());
      engineListener = newState -> ApplicationManager.getApplication().invokeLater(() -> {
        // re-fetch state, as some time might have passed until it was assigned to the EDT and things might have changed
        if (engine == null) {
          return;
        }
        setStatus(engine.getState());
      });
      ConnectedSonarLintEngine.State state = engine.getState();
      setStatus(state);
      engine.addStateListener(engineListener);
    } else {
      serverStatus.setText("[ no connection selected ]");
      updateServerButton.setEnabled(false);
    }
  }

  private void setStatus(ConnectedSonarLintEngine.State state) {
    ConnectedSonarLintEngine.State currentState = engine.getState();
    StringBuilder builder = new StringBuilder();

    switch (currentState) {
      case NEVER_UPDATED:
        builder.append("never updated");
        break;
      case UPDATED:
        GlobalStorageStatus storageStatus = engine.getGlobalStorageStatus();
        if (storageStatus != null) {
          builder.append(DateUtils.toAge(storageStatus.getLastUpdateDate().getTime()));
        } else {
          builder.append("up to date");
        }
        break;
      case UPDATING:
        builder.append("updating..");
        break;
      case NEED_UPDATE:
        builder.append("needs update");
        break;
      case UNKNOWN:
      default:
        builder.append("unknown");
        break;
    }
    serverStatus.setText(builder.toString());
    updateServerButton.setEnabled(state != ConnectedSonarLintEngine.State.UPDATING);
  }

  private void actionUpdateServerTask() {
    ServerConnection server = getSelectedConnection();
    if (server == null || engine == null || engine.getState() == ConnectedSonarLintEngine.State.UPDATING) {
      return;
    }

    updateServerBinding(server, engine, false);
  }

  public static void updateServerBinding(ServerConnection connection, ConnectedSonarLintEngine engine, boolean onlyProjects) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    Map<String, List<Project>> projectsPerModule = new HashMap<>();

    for (Project openProject : openProjects) {
      SonarLintProjectSettings projectSettings = getSettingsFor(openProject);
      String projectKey = projectSettings.getProjectKey();
      if (projectSettings.isBindingEnabled() && connection.getName().equals(projectSettings.getConnectionName()) && projectKey != null) {
        List<Project> projects = projectsPerModule.computeIfAbsent(projectKey, k -> new ArrayList<>());
        projects.add(openProject);
      }
    }

    ConnectionUpdateTask task = new ConnectionUpdateTask(engine, connection, projectsPerModule, onlyProjects);
    ProgressManager.getInstance().run(task.asBackground());
  }

  private void editSelectedConnection(boolean forNotificationsOnly) {
    ServerConnection selectedConnection = getSelectedConnection();
    int selectedIndex = connectionList.getSelectedIndex();

    if (selectedConnection != null) {
      ServerConnectionWizard serverEditor = forNotificationsOnly ? ServerConnectionWizard.forNotificationsEdition(selectedConnection) :
        ServerConnectionWizard.forConnectionEdition(selectedConnection);
      if (serverEditor.showAndGet()) {
        ServerConnection newConnection = serverEditor.getConnection();
        ((CollectionListModel<ServerConnection>) connectionList.getModel()).setElementAt(newConnection, selectedIndex);
        connections.set(connections.indexOf(selectedConnection), newConnection);
        connectionChangeListener.changed(connections);
        if (!forNotificationsOnly) {
          updateConnectionStorage(selectedConnection);
        }
      }
    }
  }

  public void editNotifications(ServerConnection connectionToEdit) {
    connectionList.setSelectedValue(connectionToEdit, true);
    editSelectedConnection(true);
  }

  @Override
  public void dispose() {
    if (engineListener != null) {
      engine.removeStateListener(engineListener);
      engineListener = null;
      engine = null;
    }
  }

  private class AddServerAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      Set<String> existingNames = connections.stream().map(ServerConnection::getName).collect(Collectors.toSet());
      ServerConnectionWizard wizard = ServerConnectionWizard.forNewConnection(existingNames);
      if (wizard.showAndGet()) {
        ServerConnection created = wizard.getConnection();
        connections.add(created);
        ((CollectionListModel<ServerConnection>) connectionList.getModel()).add(created);
        connectionList.setSelectedIndex(connectionList.getModel().getSize() - 1);
        connectionChangeListener.changed(connections);
        updateConnectionStorage(created);
      }
    }
  }

  private void updateConnectionStorage(ServerConnection created) {
    SonarLintEngineManager serverManager = SonarLintUtils.getService(SonarLintEngineManager.class);
    ConnectionUpdateTask task = new ConnectionUpdateTask(serverManager.getConnectedEngine(created.getName()), created, Collections.emptyMap(), false);
    ProgressManager.getInstance().run(task.asBackground());
  }

  private class RemoveServerAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      ServerConnection server = getSelectedConnection();
      int selectedIndex = connectionList.getSelectedIndex();

      if (server == null) {
        return;
      }

      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      List<String> projectsUsingNames = getOpenProjectNames(openProjects, server);

      if (!projectsUsingNames.isEmpty()) {
        String projects = projectsUsingNames.stream().collect(Collectors.joining("<br>"));
        int response = Messages.showYesNoDialog(serversPanel,
          "<html>The following opened projects are bound to this connection:<br><b>" +
            projects + "</b><br>Delete the connection?</html>", "Connection In Use", Messages.getWarningIcon());
        if (response == Messages.NO) {
          return;
        }
      }

      CollectionListModel<ServerConnection> model = (CollectionListModel<ServerConnection>) connectionList.getModel();
      // it's not removed from serverIds and editorList
      model.remove(server);
      connections.remove(server);
      connectionChangeListener.changed(connections);

      if (model.getSize() > 0) {
        int newIndex = Math.min(model.getSize() - 1, Math.max(selectedIndex - 1, 0));
        connectionList.setSelectedValue(model.getElementAt(newIndex), true);
      }
    }

    private List<String> getOpenProjectNames(Project[] openProjects, ServerConnection server) {
      List<String> openProjectNames = new LinkedList<>();

      for (Project openProject : openProjects) {
        SonarLintProjectSettings projectSettings = getSettingsFor(openProject);
        if (server.getName().equals(projectSettings.getConnectionName())) {
          openProjectNames.add(openProject.getName());
        }
      }
      return openProjectNames;
    }
  }
}
