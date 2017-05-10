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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.Convertor;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;

import static javax.swing.JList.VERTICAL;

public class OrganizationSelector extends DialogWrapper {
  private final SonarQubeServer server;
  private JBList<RemoteOrganization> orgList;
  private String selectedOrganizationKey;
  private JPanel panel;

  protected OrganizationSelector(@NotNull Component parent, SonarQubeServer server) {
    super(parent, true);
    this.server = server;
    super.setTitle("List SonarQube Organizations");
    super.setModal(true);
    super.setResizable(true);
    this.setOKActionEnabled(false);
    super.init();
  }

  @Nullable @Override protected JComponent createCenterPanel() {
    panel = new JPanel(new BorderLayout(10, 10));
    JBLabel text = new JBLabel("Choose a organization from the list. Type to search.");
    orgList = new JBList<>();
    orgList.setLayoutOrientation(VERTICAL);
    orgList.setVisibleRowCount(8);
    orgList.setEnabled(false);
    orgList.setEmptyText("Fetching list from SonarQube server..");
    orgList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    orgList.setCellRenderer(new ListRenderer());
    orgList.addMouseListener(new MouseDoubleClickAdapter());
    orgList.addListSelectionListener(e -> organizationSelected());

    Convertor<Object, String> convertor = o -> {
      RemoteOrganization org = (RemoteOrganization) o;
      return org.getName() + " " + org.getKey();
    };
    new ListSpeedSearch(orgList, convertor);

    panel.add(text, BorderLayout.NORTH);
    JScrollPane scrollPane = new JScrollPane(orgList);
    panel.add(scrollPane, BorderLayout.CENTER);
    new UpdateTask().queue();
    return panel;
  }

  private class MouseDoubleClickAdapter extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2 && orgList.getSelectedValue() != null) {
        organizationSelected();
        OrganizationSelector.this.close(0, true);
      }
    }
  }

  private void organizationSelected() {
    this.selectedOrganizationKey = orgList.getSelectedValue().getKey();
    this.setOKActionEnabled(true);
  }

  private class UpdateTask extends Task.Backgroundable {
    public UpdateTask() {
      super(null, "Updating List of Organizations", true);
    }

    @Override
    public void run(ProgressIndicator progress) {
      try {
        ServerConfiguration serverConfiguration = SonarLintUtils.getServerConfiguration(server);
        WsHelperImpl impl = new WsHelperImpl();
        List<RemoteOrganization> orgs = impl.listOrganizations(serverConfiguration, null);
        ApplicationManager.getApplication().invokeLater(() -> setResults(orgs), ModalityState.stateForComponent(panel));
      } catch (Exception e) {
        ApplicationManager.getApplication().invokeLater(() -> {
          Messages.showMessageDialog(panel, "Please check your server configuration: " + e.getMessage(),
            "Failed to Download List of Organizations", Messages.getWarningIcon());
          OrganizationSelector.super.close(0, false);
        }, ModalityState.stateForComponent(panel));
      }
    }

    public void setResults(List<RemoteOrganization> orgs) {
      orgList.setListData(orgs.toArray(new RemoteOrganization[orgs.size()]));
      orgList.setEnabled(true);
      orgList.requestFocusInWindow();
    }
  }

  public String getSelectedOrganizationKey() {
    return selectedOrganizationKey;
  }

  private class ListRenderer extends ColoredListCellRenderer<RemoteOrganization> {
    @Override protected void customizeCellRenderer(JList list, @Nullable RemoteOrganization value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        return;
      }

      append(value.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true);
      // it is not working: appendTextPadding
      append(" ");
      if (index >= 0) {
        append("(" + value.getKey() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
      }
    }
  }
}
