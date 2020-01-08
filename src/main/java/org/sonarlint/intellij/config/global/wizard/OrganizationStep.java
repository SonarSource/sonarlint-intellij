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
package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.tasks.GetOrganizationTask;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;

import static javax.swing.JList.VERTICAL;

public class OrganizationStep extends AbstractWizardStepEx {
  private final WizardModel model;
  private JList<RemoteOrganization> orgList;
  private JPanel panel;
  private JButton selectOtherOrganizationButton;
  private DefaultListModel<RemoteOrganization> listModel;

  public OrganizationStep(WizardModel model) {
    super("Organization");
    this.model = model;

    orgList.addListSelectionListener(e -> fireStateChanged());
    orgList.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && isComplete()) {
          OrganizationStep.super.fireGoNext();
        }
      }
    });
    selectOtherOrganizationButton.addActionListener(e -> enterCustomOrganizationKey());
  }

  private void enterCustomOrganizationKey() {
    while (true) {
      String organizationKey = Messages.showInputDialog(panel, "Please enter the organization key", "Add Another Organization", null);
      if (StringUtil.isNotEmpty(organizationKey)) {
        boolean found = selectOrganizationIfExists(organizationKey);
        if (found) {
          break;
        }

        GetOrganizationTask task = new GetOrganizationTask(model.createServerWithoutOrganization(), organizationKey);
        ProgressManager.getInstance().run(task);

        if (task.organization().isPresent()) {
          listModel.add(0, task.organization().get());
          orgList.setSelectedIndex(0);
          orgList.ensureIndexIsVisible(0);
          break;
        } else if (task.getException() != null) {
          Messages.showErrorDialog("Failed to fetch organization from server: " + task.getException().getMessage(), "Connection Failure");
        } else {
          Messages.showErrorDialog(String.format("Organization '%s' not found. Please enter the key of an existing organization.", organizationKey), "Organization Not Found");
        }
      } else {
        break;
      }
    }
  }

  private void save() {
    RemoteOrganization org = orgList.getSelectedValue();
    if (org != null) {
      model.setOrganizationKey(org.getKey());
    } else {
      model.setOrganizationKey(null);
    }
  }

  @Override
  public void _init() {
    listModel = new DefaultListModel<>();
    model.getOrganizationList().forEach(listModel::addElement);
    orgList.setModel(listModel);
    // automatically focus and select a row to be possible to search immediately
    orgList.grabFocus();
    if (model.getOrganizationKey() != null) {
      // this won't work if it was a custom organization
      selectOrganizationIfExists(model.getOrganizationKey());
    } else if (!listModel.isEmpty()) {
      orgList.setSelectedIndex(0);
    }
  }

  private boolean selectOrganizationIfExists(String organizationKey) {
    for (int i = 0; i < listModel.getSize(); i++) {
      RemoteOrganization org = listModel.getElementAt(i);
      if (organizationKey.equals(org.getKey())) {
        orgList.setSelectedIndex(i);
        orgList.ensureIndexIsVisible(i);
        return true;
      }
    }
    return false;
  }

  @NotNull @Override public Object getStepId() {
    return OrganizationStep.class;
  }

  @Nullable @Override public Object getNextStepId() {
    return ConfirmStep.class;
  }

  @Nullable @Override public Object getPreviousStepId() {
    return AuthStep.class;
  }

  @Override public boolean isComplete() {
    // if this step is skipped because there is only one organization, we still need to return true so that the wizard
    // can finish
    return model.getOrganizationList().size() <= 1 || orgList.getSelectedValue() != null;
  }

  @Override public void commit(CommitType commitType) {
    if (commitType == CommitType.Finish || commitType == CommitType.Next) {
      save();
    }
  }

  @Override public JComponent getComponent() {
    return panel;
  }

  @Nullable @Override public JComponent getPreferredFocusedComponent() {
    return orgList;
  }

  private void createUIComponents() {
    JBList<RemoteOrganization> list = new JBList<>();
    list.setLayoutOrientation(VERTICAL);
    list.setVisibleRowCount(8);
    list.setEnabled(true);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new ListRenderer());
    new ListSpeedSearch<>(list, (Function<RemoteOrganization, String>) o -> o.getName() + " " + o.getKey());
    orgList = list;
  }

  private static class ListRenderer extends ColoredListCellRenderer<RemoteOrganization> {
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
