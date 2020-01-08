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
import org.sonarlint.intellij.config.global.wizard.SQServerWizard;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.tasks.ServerUpdateTask;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.util.DateUtils;

public class SonarQubeServerMgmtPanel implements Disposable, ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String LABEL_NO_SERVERS = "Add a connection to SonarQube or SonarCloud";

  // UI
  private JPanel panel;
  private Splitter splitter;
  private JPanel serversPanel;
  private JBList<SonarQubeServer> serverList;
  private JPanel emptyPanel;
  private JLabel serverStatus;
  private JButton updateServerButton;

  // Model
  private GlobalConfigurationListener serverChangeListener;
  private SonarLintEngineManager serverManager;
  private final List<SonarQubeServer> servers = new ArrayList<>();
  private final Set<String> deletedServerIds = new HashSet<>();
  private ConnectedSonarLintEngine engine = null;
  private StateListener engineListener;

  private void create() {
    Application app = ApplicationManager.getApplication();
    serverManager = app.getComponent(SonarLintEngineManager.class);
    serverChangeListener = app.getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC);
    serverList = new JBList<>();
    serverList.getEmptyText().setText(LABEL_NO_SERVERS);
    serverList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent evt) {
        if (evt.getClickCount() == 2) {
          editServer();
        }
      }
    });
    serverList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        onServerSelect();
      }
    });

    serversPanel = new JPanel(new BorderLayout());

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(serverList)
      .setEditActionName("Edit")
      .setEditAction(e -> editServer())
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

    serverList.setCellRenderer(new ColoredListCellRenderer<SonarQubeServer>() {
      @Override
      protected void customizeCellRenderer(JList list, SonarQubeServer server, int index, boolean selected, boolean hasFocus) {
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
      @Override public void actionPerformed(ActionEvent e) {
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

    for (Project p : openProjects) {
      SonarLintProjectSettings projectSettings = SonarLintUtils.get(p, SonarLintProjectSettings.class);
      if (projectSettings.getServerId() != null && deletedServerIds.contains(projectSettings.getServerId())) {
        projectSettings.setBindingEnabled(false);
        projectSettings.setServerId(null);
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
    return !servers.equals(settings.getSonarQubeServers());
  }

  @Override
  public void save(SonarLintGlobalSettings settings) {
    List<SonarQubeServer> copyList = new ArrayList<>(servers);
    settings.setSonarQubeServers(copyList);

    //remove them even if a server with the same name was later added
    unbindRemovedServers();
  }

  @Override
  public void load(SonarLintGlobalSettings settings) {
    servers.clear();
    deletedServerIds.clear();

    CollectionListModel<SonarQubeServer> listModel = new CollectionListModel<>(new ArrayList<>());
    listModel.add(settings.getSonarQubeServers());
    servers.addAll(settings.getSonarQubeServers());
    serverList.setModel(listModel);

    if (!servers.isEmpty()) {
      serverList.setSelectedValue(servers.get(0), true);
    }
  }

  @Nullable
  private SonarQubeServer getSelectedServer() {
    return serverList.getSelectedValue();
  }

  private void onServerSelect() {
    switchTo(getSelectedServer());
  }

  private void switchTo(@Nullable SonarQubeServer server) {
    if (engineListener != null) {
      engine.removeStateListener(engineListener);
      engineListener = null;
      engine = null;
    }

    if (server != null) {
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
      case UNKNOW:
      default:
        builder.append("unknown");
        break;
    }
    serverStatus.setText(builder.toString());
    updateServerButton.setEnabled(state != ConnectedSonarLintEngine.State.UPDATING);
  }

  private void actionUpdateServerTask() {
    SonarQubeServer server = getSelectedServer();
    if (server == null || engine == null || engine.getState() == ConnectedSonarLintEngine.State.UPDATING) {
      return;
    }

    updateServerBinding(server, engine, false);
  }

  public static void updateServerBinding(SonarQubeServer server, ConnectedSonarLintEngine engine, boolean onlyProjects) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    Map<String, List<Project>> projectsPerModule = new HashMap<>();

    for (Project p : openProjects) {
      SonarLintProjectSettings projectSettings = SonarLintUtils.get(p, SonarLintProjectSettings.class);
      String projectKey = projectSettings.getProjectKey();
      if (projectSettings.isBindingEnabled() && server.getName().equals(projectSettings.getServerId()) && projectKey != null) {
        List<Project> projects = projectsPerModule.computeIfAbsent(projectKey, k -> new ArrayList<>());
        projects.add(p);
      }
    }

    ServerUpdateTask task = new ServerUpdateTask(engine, server, projectsPerModule, onlyProjects);
    ProgressManager.getInstance().run(task.asBackground());
  }

  private void editServer() {
    SonarQubeServer selectedServer = getSelectedServer();
    int selectedIndex = serverList.getSelectedIndex();

    if (selectedServer != null) {
      SQServerWizard serverEditor = new SQServerWizard(selectedServer);
      if (serverEditor.showAndGet()) {
        SonarQubeServer newServer = serverEditor.getServer();
        ((CollectionListModel<SonarQubeServer>) serverList.getModel()).setElementAt(newServer, selectedIndex);
        servers.set(servers.indexOf(selectedServer), newServer);
        serverChangeListener.changed(servers);
      }
    }
  }

  @Override public void dispose() {
    if (engineListener != null) {
      engine.removeStateListener(engineListener);
      engineListener = null;
      engine = null;
    }
  }

  private class AddServerAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      Set<String> existingNames = servers.stream().map(SonarQubeServer::getName).collect(Collectors.toSet());
      SQServerWizard wizard = new SQServerWizard(existingNames);
      if (wizard.showAndGet()) {
        SonarQubeServer created = wizard.getServer();
        servers.add(created);
        ((CollectionListModel<SonarQubeServer>) serverList.getModel()).add(created);
        serverList.setSelectedIndex(serverList.getModel().getSize() - 1);
        serverChangeListener.changed(servers);
        ServerUpdateTask task = new ServerUpdateTask(serverManager.getConnectedEngine(created.getName()), created, Collections.emptyMap(), false);
        ProgressManager.getInstance().run(task.asBackground());
      }
    }
  }

  private class RemoveServerAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      SonarQubeServer server = getSelectedServer();
      int selectedIndex = serverList.getSelectedIndex();

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

      CollectionListModel<SonarQubeServer> model = (CollectionListModel<SonarQubeServer>) serverList.getModel();
      // it's not removed from serverIds and editorList
      model.remove(server);
      servers.remove(server);
      serverChangeListener.changed(servers);

      if (model.getSize() > 0) {
        int newIndex = Math.min(model.getSize() - 1, Math.max(selectedIndex - 1, 0));
        serverList.setSelectedValue(model.getElementAt(newIndex), true);
      }
    }

    private List<String> getOpenProjectNames(Project[] openProjects, SonarQubeServer server) {
      List<String> openProjectNames = new LinkedList<>();

      for (Project p : openProjects) {
        SonarLintProjectSettings projectSettings = SonarLintUtils.get(p, SonarLintProjectSettings.class);
        if (server.getName().equals(projectSettings.getServerId())) {
          openProjectNames.add(p.getName());
        }
      }
      return openProjectNames;
    }
  }
}
