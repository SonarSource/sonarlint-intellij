/**
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
package org.sonarlint.intellij.config.global;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.SonarLintServerManager;
import org.sonarlint.intellij.util.ResourceLoader;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SonarQubeServerMgmtPanel {
  private static final Logger LOGGER = Logger.getInstance(SonarQubeServerMgmtPanel.class);
  private static final String LABEL_NO_SERVERS = "No servers";
  private static final String EMPTY_CARD_NAME = "empty";
  private static final String LIST_ICON = ResourceLoader.ICON_SONARQUBE_16;

  // UI
  private JPanel panel;
  private Splitter splitter;
  private JPanel serversPanel;
  private JBList serverList;
  private JPanel editorPanel;
  private JPanel emptyPanel;

  // Model
  private List<SonarQubeServer> servers = new ArrayList<>();
  private Consumer<SonarQubeServer> changeListener;

  // Unique IDs to assign to the cards in the CardLayout
  private final FactoryMap<SonarQubeServer, String> serverIds = new ConcurrentFactoryMap<SonarQubeServer, String>() {
    private int count;
    @Override
    protected String create(SonarQubeServer server) {
      return Integer.toString(count++);
    }
  };

  public SonarQubeServerMgmtPanel(SonarLintGlobalSettings settings) {
    create();
    load(settings);
  }

  public void create() {
    serverList = new JBList();
    serverList.getEmptyText().setText(LABEL_NO_SERVERS);

    serversPanel = new JPanel(new BorderLayout());

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(serverList).disableUpDownActions();

    toolbarDecorator.setAddAction(new AddServerAction());

    toolbarDecorator.setRemoveAction(new RemoveServerAction());

    JBLabel serversLabel = new JBLabel("Configured servers:");
    serversLabel.setLabelFor(serverList);
    serversPanel.add(serversLabel, BorderLayout.NORTH);
    serversPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);

    editorPanel = new JPanel(new CardLayout());

    splitter = new Splitter(true);
    splitter.setFirstComponent(serversPanel);
    splitter.setSecondComponent(editorPanel);

    JBLabel emptyLabel = new JBLabel("No server selected", SwingConstants.CENTER);
    emptyPanel = new JPanel(new BorderLayout());
    emptyPanel.add(emptyLabel, BorderLayout.CENTER);

    panel = new JPanel(new BorderLayout());
    panel.add(splitter);

    serverList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        SonarQubeServer server = getSelectedServer();
        if (server != null) {
          String name = serverIds.get(server);
          ((CardLayout) editorPanel.getLayout()).show(editorPanel, name);
          splitter.doLayout();
          splitter.repaint();
        }
      }
    });

    serverList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        SonarQubeServer server = (SonarQubeServer)value;
        try {
          setIcon(ResourceLoader.getIcon(LIST_ICON));
        } catch (IOException e) {
          LOGGER.error("Error loading SonarLint icon", e);
        }

        append(server.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });

    changeListener = new Consumer<SonarQubeServer>() {
      @Override
      public void consume(SonarQubeServer server) {
        ((CollectionListModel)serverList.getModel()).contentsChanged(server);
      }
    };
  }

  public JComponent getComponent() {
    return panel;
  }

  private void validateConfiguration() throws ConfigurationException {
    Set<String> names = new HashSet<>();

    for(SonarQubeServer s : servers) {
      if(StringUtils.isEmpty(s.getName())) {
        serverList.setSelectedValue(s, true);
        throw new ConfigurationException("Servers must be configured with a name");
      }

      if(StringUtils.isEmpty(s.getHostUrl())) {
        serverList.setSelectedValue(s, true);
        throw new ConfigurationException("Servers must be configured with a host URL");
      }

      if(names.contains(s.getName())) {
        serverList.setSelectedValue(s, true);
        throw new ConfigurationException("Servers must have a unique name");
      }

      names.add(s.getName());
    }
  }

  public boolean isModified(SonarLintGlobalSettings settings) {
    return !servers.equals(settings.getSonarQubeServers());
  }

  public void save(SonarLintGlobalSettings settings) throws ConfigurationException {
    validateConfiguration();
    List<SonarQubeServer> copyList = new LinkedList<>();
    for(SonarQubeServer s : servers) {
      copyList.add(new SonarQubeServer(s));
    }
    settings.setSonarQubeServers(copyList);
  }

  public void load(SonarLintGlobalSettings settings) {
    serverIds.clear();
    editorPanel.removeAll();
    editorPanel.add(emptyPanel, EMPTY_CARD_NAME);
    servers.clear();

    CollectionListModel<SonarQubeServer> listModel = new CollectionListModel<>(new ArrayList<SonarQubeServer>());

    for (SonarQubeServer s : settings.getSonarQubeServers()) {
      SonarQubeServer copy = new SonarQubeServer(s);
      listModel.add(copy);
      servers.add(copy);
      addServerEditor(copy);
    }

    serverList.setModel(listModel);

    if (!servers.isEmpty()) {
      serverList.setSelectedValue(servers.get(0), true);
    }
  }

  @Nullable
  private SonarQubeServer getSelectedServer() {
    return (SonarQubeServer) serverList.getSelectedValue();
  }

  private void addServerEditor(SonarQubeServer server) {
    SonarQubeServerEditorPanel editor = new SonarQubeServerEditorPanel(changeListener, server);

    JComponent component = editor.create();
    String name = serverIds.get(server);
    editorPanel.add(component, name);
    editorPanel.doLayout();
  }

  private class AddServerAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      Icon icon = null;
      try {
        icon = ResourceLoader.getIcon(LIST_ICON);
      } catch (IOException e) {
        LOGGER.error("Error loading SonarLint icon", e);
      }

      String name = Messages.showInputDialog(serversPanel, "Choose a name for the new configuration", "Create SonarQube server configuration", icon, null, new InputValidator() {
        @Override public boolean checkInput(String inputString) {
          if(StringUtils.isBlank(inputString)) {
            return false;
          }

          for(SonarQubeServer s : servers) {
            if(s.getName().equals(inputString)) {
              return false;
            }
          }
          return true;
        }

        @Override public boolean canClose(String inputString) {
          return checkInput(inputString);
        }
      });

      if(name == null) {
        //cancelled
        return;
      }
      SonarQubeServer newServer = new SonarQubeServer();
      newServer.setName(name.trim());
      servers.add(newServer);
      ((CollectionListModel)serverList.getModel()).add(newServer);
      addServerEditor(newServer);
      serverList.setSelectedIndex(serverList.getModel().getSize() - 1);
    }
  }

  private class RemoveServerAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      SonarQubeServer server = getSelectedServer();

      if (server == null) {
        return;
      }

      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

      Set<String> projectsUsing = new HashSet<>();
      for(Project p : openProjects) {
        SonarLintProjectSettings projectSettings = p.getComponent(SonarLintProjectSettings.class);
        if(projectSettings.getServerId() != null && projectSettings.getServerId().equals(server.getName())) {
          projectsUsing.add(p.getName());
        }
      }

      if(!projectsUsing.isEmpty()) {
        int response = Messages.showYesNoDialog(serversPanel,
          "<html>The following opened projects are bound to this server configuration:<br><b>" +
            StringUtils.join(projectsUsing, "<br>") + "</b><br>Are you sure you want to delete the server?</html>", "Server configuration in use", Messages.getWarningIcon());
        if(response == Messages.NO) {
          return;
        }
      }

      // remove storage
      SonarLintServerManager serverManager = ApplicationManager.getApplication().getComponent(SonarLintServerManager.class);
      serverManager.getConnectedEngine(server.getName()).stop(true);

      CollectionListModel model = (CollectionListModel) serverList.getModel();
      // it's not removed from serverIds and editorList
      model.remove(server);
      servers.remove(server);

      if (model.getSize() > 0) {
        serverList.setSelectedValue(model.getElementAt(0), true);
      }
      else {
        ((CardLayout) editorPanel.getLayout()).show(editorPanel, EMPTY_CARD_NAME);
      }
    }
  }
}
