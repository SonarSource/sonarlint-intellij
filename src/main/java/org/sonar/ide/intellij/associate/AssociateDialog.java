/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.config.SonarQubeSettings;
import org.sonar.ide.intellij.console.SonarQubeConsole;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.util.SonarQubeBundle;
import org.sonar.ide.intellij.wsclient.ISonarRemoteProject;
import org.sonar.ide.intellij.wsclient.ISonarWSClientFacade;
import org.sonar.ide.intellij.wsclient.WSClientFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AssociateDialog extends DialogWrapper {

  private static final Logger LOG = Logger.getInstance(AssociateDialog.class);

  private JPanel contentPane;
  private ComboBox projectComboBox;

  public AssociateDialog(@Nullable Project project) {
    super(project, true);
    init();
    setTitle(SonarQubeBundle.message("sonarqube.associate.title"));
    projectComboBox.setRenderer(new ListCellRendererWrapper<ISonarRemoteProject>() {
      @Override
      public void customize(JList list, ISonarRemoteProject value, int index, boolean isSelected, boolean cellHasFocus) {
        ISonarRemoteProject sonarProject = (ISonarRemoteProject) value;
        setText(sonarProject != null ? sonarProject.getName() + " (" + sonarProject.getServer().getId() + ")" : null);
      }
    });
    projectComboBox.setEditor(new AssociateComboBoxEditor());
    List<ISonarRemoteProject> allProjects = new ArrayList<ISonarRemoteProject>();
    for (SonarQubeServer server : SonarQubeSettings.getInstance().getServers()) {
      ISonarWSClientFacade sonarClient = WSClientFactory.getInstance().getSonarClient(server);
      try {
        allProjects.addAll(sonarClient.listAllRemoteProjects());
      } catch (Exception e) {
        SonarQubeConsole.getSonarQubeConsole(project).error("Unable to retrieve list of remote projects from server " + server.getId() + ": " + e.getMessage());
        LOG.warn("Unable to retrieve list of remote projects from server " + server.getId(), e);
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
    for (ISonarRemoteProject module : allProjects) {
      projectComboBox.addItem(module);
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public ISonarRemoteProject getSelectedSonarQubeProject() {
    return (ISonarRemoteProject) projectComboBox.getSelectedItem();
  }

  public void setSelectedSonarQubeProject(@Nullable String serverId, @Nullable String moduleKey) {
    for (int i = 0; i < projectComboBox.getModel().getSize(); i++) {
      ISonarRemoteProject module = (ISonarRemoteProject) projectComboBox.getModel().getElementAt(i);
      if (module.getServer().getId().equals(serverId) && module.getKey().equals(moduleKey)) {
        projectComboBox.setSelectedIndex(i);
        return;
      }
    }
    projectComboBox.setSelectedIndex(-1);
  }
}
