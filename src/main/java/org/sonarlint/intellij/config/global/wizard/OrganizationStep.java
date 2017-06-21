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
package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.Convertor;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteOrganization;

import static javax.swing.JList.VERTICAL;

public class OrganizationStep extends AbstractWizardStepEx {
  private final WizardModel model;
  private JList orgList;
  private JPanel panel;

  public OrganizationStep(WizardModel model) {
    super("Organization");
    this.model = model;
  }

  private void save() {
    RemoteOrganization org = (RemoteOrganization) orgList.getSelectedValue();
    if (org != null) {
      model.setOrganization(org.getKey());
    } else {
      model.setOrganization(null);
    }
  }

  @Override
  public void _init() {
    List<RemoteOrganization> list = model.getOrganizationList();
    int size = list.size();
    orgList.setListData(list.toArray(new RemoteOrganization[size]));
    orgList.addListSelectionListener(e -> fireStateChanged());
    if (model.getOrganization() != null) {
      for (int i = 0; i < orgList.getModel().getSize(); i++) {
        RemoteOrganization org = (RemoteOrganization) orgList.getModel().getElementAt(i);
        if (model.getOrganization().equals(org.getKey())) {
          orgList.setSelectedIndex(i);
          orgList.ensureIndexIsVisible(i);
          break;
        }
      }
    }
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
    return model.getOrganizationList().size() <= 1 || orgList.getSelectedValue() != null;
  }

  @Override public void commit(CommitType commitType) throws CommitStepException {
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
    JBList list = new JBList();
    list.setLayoutOrientation(VERTICAL);
    list.setVisibleRowCount(8);
    list.setEnabled(true);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new ListRenderer());

    Convertor<Object, String> convertor = o -> {
      RemoteOrganization org = (RemoteOrganization) o;
      return org.getName() + " " + org.getKey();
    };
    new ListSpeedSearch(list, convertor);
    this.orgList = list;
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
