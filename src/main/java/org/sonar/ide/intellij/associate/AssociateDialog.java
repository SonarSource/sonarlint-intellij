/*
 * SonarQube IntelliJ
 * Copyright (C) 2013-2014 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.associate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.config.SonarQubeSettings;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.util.SonarQubeBundle;
import org.sonar.ide.intellij.wsclient.ISonarRemoteProject;
import org.sonar.ide.intellij.wsclient.ISonarWSClientFacade;
import org.sonar.ide.intellij.wsclient.WSClientFactory;

import javax.annotation.CheckForNull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public class AssociateDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(AssociateDialog.class);
  public static final int UNASSOCIATE_EXIT_CODE = NEXT_USER_EXIT_CODE;
  public static final String UNABLE_TO_CONNECT_MSG = "Unable to connect to SonarQube server %s. Please check settings.";

  private final Project project;
  private JPanel contentPane;
  private FilterComponent projectFilter;
  private JList projectList;
  private Map<SonarQubeServer, ISonarWSClientFacade> serverClients = new HashMap<SonarQubeServer, ISonarWSClientFacade>();
  private final boolean displayUnassociateButton;

  public AssociateDialog(@NotNull Project project, boolean displayUnassociateButton) {
    super(project, true);
    this.project = project;
    this.displayUnassociateButton = displayUnassociateButton;
    init();
    setTitle(SonarQubeBundle.message("sonarqube.associate.title"));
    for (SonarQubeServer server : SonarQubeSettings.getInstance().getServers()) {
      try {
        ISonarWSClientFacade sonarClient = WSClientFactory.getInstance().getSonarClient(server);
        if (sonarClient.testConnection().getStatus() != ISonarWSClientFacade.ConnectionTestResult.Status.OK) {
          LOG.error(String.format(UNABLE_TO_CONNECT_MSG, server.getId()));
          continue;
        }
        serverClients.put(server, sonarClient);
      } catch (Exception e) {
        LOG.error(String.format(UNABLE_TO_CONNECT_MSG, server.getId()), e);
      }
    }
    if (serverClients.isEmpty()) {
      LOG.error("No SonarQube server is properly configured. Association is not possible.");
    }
    projectList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        ISonarRemoteProject sonarProject = (ISonarRemoteProject) value;
        String label = sonarProject != null ? sonarProject.getName() + " (" + sonarProject.getServer().getId() + ")" : null;
        return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
      }
    });
  }

  public void createUIComponents() {
    projectFilter = new ServerFilterComponent();
    projectFilter.getTextEditor().setToolTipText(SonarQubeBundle.message("sonarqube.associate.comboToolTip"));
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (getSelectedSonarQubeProject() == null) {
      return new ValidationInfo("Please select a SonarQube project to associate with", projectList);
    }
    return null;
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    if (displayUnassociateButton) {
      return new Action[]{
          new AbstractAction(SonarQubeBundle.message("sonarqube.associate.remove")) {

            @Override
            public void actionPerformed(ActionEvent e) {
              projectList.setSelectedIndex(-1);
              close(UNASSOCIATE_EXIT_CODE);
            }
          }
      };
    } else {
      return super.createLeftSideActions();
    }
  }

  @Nullable
  public ISonarRemoteProject getSelectedSonarQubeProject() {
    return (ISonarRemoteProject) projectList.getSelectedValue();
  }

  public void setFilter(String projectName) {
    projectFilter.setFilter(projectName);
  }

  public void setSelectedSonarQubeProject(@Nullable String serverId, @Nullable String projectKey) {
    if (serverId == null) {
      return;
    }
    ISonarWSClientFacade serverClient = findServerClient(serverId);
    if (serverClient == null) {
      return;
    }
    List<ISonarRemoteProject> remoteProjects = serverClient.listAllRemoteProjects();
    for (ISonarRemoteProject remoteProject : remoteProjects) {
      if (remoteProject.getKey().equals(projectKey)) {
        DefaultListModel model = new DefaultListModel();
        model.addElement(remoteProject);
        projectList.setModel(model);
        projectList.setSelectedValue(remoteProject, true);
        return;
      }
    }
  }

  @CheckForNull
  private ISonarWSClientFacade findServerClient(String serverId) {
    for (Map.Entry<SonarQubeServer, ISonarWSClientFacade> serverClient : serverClients.entrySet()) {
      if (serverClient.getKey().getId().equals(serverId)) {
        return serverClient.getValue();
      }
    }
    return null;
  }

  private class ServerFilterComponent extends FilterComponent {
    @Override
    public void filter() {
      List<ISonarRemoteProject> allProjects = new ArrayList<ISonarRemoteProject>();
      for (Map.Entry<SonarQubeServer, ISonarWSClientFacade> serverClient : serverClients.entrySet()) {
        try {
          allProjects.addAll(serverClient.getValue().searchRemoteProjects(getFilter()));
        } catch (Exception e) {
          String msg = "Unable to retrieve list of remote projects from server " + serverClient.getKey().getId();
          SonarQubeConsole.getSonarQubeConsole(project).error( msg + ": " + e.getMessage());
          LOG.warn(msg, e);
        }
      }
      Collections.sort(allProjects, new Comparator<ISonarRemoteProject>() {
        @Override
        public int compare(ISonarRemoteProject o1, ISonarRemoteProject o2) {
          if (o1.getName().equals(o2.getName())) {
            return o1.getServer().getId().compareTo(o2.getServer().getId());
          }
          return o1.getName().compareTo(o2.getName());
        }
      });
      Object previouslySelectedValue = projectList.getSelectedValue();
      DefaultListModel model = new DefaultListModel();
      projectList.setModel(model);
      for (ISonarRemoteProject module : allProjects) {
        model.addElement(module);
      }
      if (previouslySelectedValue != null) {
        projectList.setSelectedValue(previouslySelectedValue, true);
      } else if (model.size() == 1) {
        projectList.setSelectedIndex(0);
      } else {
        projectList.setSelectedIndex(-1);
      }
    }
  }
}
