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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class SonarLintProjectIgnoredRulesPanel
{
  private SortedListModel<String> listModel = new SortedListModel<>(String.CASE_INSENSITIVE_ORDER);

  private JBList<String> list;

  public Set<String> getExclusions()
  {
    return new HashSet<>(listModel.getItems());
  }

  public void setExclusions(Set<String> data)
  {
    listModel.addAll(data);
    list.setModel(listModel);
  }

  public JPanel create()
  {
    list = new JBList<>(listModel);
    list.setEmptyText("No rules ignored locally");

    JPanel panel = ToolbarDecorator.createDecorator(list)
      .setAddAction(new AddRuleAction())
      .createPanel();

    panel.setBorder(BorderFactory.createTitledBorder("Ignored Rules"));
    return panel;
  }

  private class AddRuleAction implements AnActionButtonRunnable
  {
    @Override
    public void run(AnActionButton anActionButton)
    {
      String newRule = Messages.showInputDialog(anActionButton.getContextComponent(), "Enter new rule ID",
        "Add New Rule", null, null, new NonEmptyInputValidator());
      listModel.add(newRule);
      ((DefaultListModel) list.getModel()).addElement(newRule);
      list.setSelectedIndex(list.getModel().getSize() - 1);
    }
  }

}
