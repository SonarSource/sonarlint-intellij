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
package org.sonar.ide.intellij.config;

import com.intellij.openapi.components.PersistentStateComponent;
import org.sonar.ide.intellij.model.SonarQubeServer;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListModel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class SonarQubeSettingsForm implements PersistentStateComponent<SonarQubeSettings> {

  private JPanel formComponent;
  private JButton addButton;
  private JComboBox<SonarQubeServer> serversList;
  private JButton editButton;
  private JButton removeButton;

  private final SonarQubeSettings settings = new SonarQubeSettings();
  private boolean modified = false;

  public SonarQubeSettingsForm(SonarQubeSettings settings) {
    loadState(settings);
    addButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        SonarQubeServerDialog dialog = new SonarQubeServerDialog();
        dialog.pack();
        dialog.setVisible(true);
        if (dialog.getServer() != null) {
          serversList.addItem(dialog.getServer());
          modified = true;
        }
      }
    });
  }

  private void createUIComponents() {
    serversList = new JComboBox<SonarQubeServer>();
  }

  public JComponent getFormComponent() {
    return formComponent;
  }

  public boolean isModified(SonarQubeSettings state) {
    return modified;
  }

  public final SonarQubeSettings getState() {
    ListModel<SonarQubeServer> model = serversList.getModel();
    settings.getServers().clear();
    for (int i = 0; i < model.getSize(); i++) {
      settings.getServers().add(model.getElementAt(i));
    }
    return settings;
  }

  public final void loadState(SonarQubeSettings state) {
    DefaultListModel model = new DefaultListModel();
    for (SonarQubeServer server : state.getServers()) {
      model.addElement(server);
    }
    modified = false;
  }

}
