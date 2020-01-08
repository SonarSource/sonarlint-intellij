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
package org.sonarlint.intellij.config.component;

import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.JPanel;

public class EditableList<T> {
  private final Supplier<T> onAdd;
  private final Function<T, T> onEdit;
  private final JBList list;
  private final JPanel listPanel;
  private final CollectionListModel<T> model;

  public EditableList(String emptyLabel, Supplier<T> onAdd, Function<T, T> onEdit) {
    this.onAdd = onAdd;
    this.onEdit = onEdit;
    list = new JBList();
    list.getEmptyText().setText(emptyLabel);
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent evt) {
        if (evt.getClickCount() == 2) {
          editEntry();
        }
      }
    });
    model = new CollectionListModel<>(new ArrayList<>());
    list.setModel(model);

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(list)
      .setEditActionName("Edit")
      .setEditAction(e -> editEntry())
      .setAddAction(new AddEntryAction())
      .setRemoveAction(new RemoveEntryAction())
      .disableUpDownActions();

    listPanel = new JPanel(new BorderLayout());
    listPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
  }

  public JPanel getComponent() {
    return listPanel;
  }

  public void set(List<T> data) {
    model.removeAll();
    for (T s : data) {
      model.add(s);
    }
  }

  public List<T> get() {
    return new ArrayList<>(model.getItems());
  }

  private void editEntry() {
    int selectedIndex = list.getSelectedIndex();
    if (selectedIndex >= 0) {
      T value = model.getElementAt(selectedIndex);
      T newValue = onEdit.apply(value);
      if (newValue != null) {
        model.setElementAt(newValue, selectedIndex);
      }
    }
  }

  private class AddEntryAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      T input = onAdd.get();
      if (input != null) {
        model.add(input);
        list.setSelectedIndex(list.getModel().getSize() - 1);
      }
    }
  }

  private class RemoveEntryAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      T entry = getSelectedEntry();
      if (entry == null) {
        return;
      }
      int selectedIndex = list.getSelectedIndex();
      model.remove(entry);

      if (model.getSize() > 0) {
        int newIndex = Math.min(model.getSize() - 1, Math.max(selectedIndex - 1, 0));
        list.setSelectedValue(model.getElementAt(newIndex), true);
      }
    }

    @Nullable
    private T getSelectedEntry() {
      return (T) list.getSelectedValue();
    }
  }

  public void add(T value) {
    model.add(value);
    list.setSelectedIndex(model.getSize() - 1);
  }
}
