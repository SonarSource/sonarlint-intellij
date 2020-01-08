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
package org.sonarlint.intellij.config.project;

import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.apache.commons.lang.StringUtils;

public class ExclusionTable {
  private final Supplier<ExclusionItem> onAdd;
  private final Function<ExclusionItem, ExclusionItem> onEdit;
  private final JBTable table;
  private final JPanel panel;
  private final Model model;

  public ExclusionTable(String emptyLabel, Supplier<ExclusionItem> onAdd, Function<ExclusionItem, ExclusionItem> onEdit) {
    this.onAdd = onAdd;
    this.onEdit = onEdit;

    model = new Model();
    table = new JBTable(model);
    table.setShowGrid(false);
    table.setIntercellSpacing(JBUI.emptySize());
    table.getEmptyText().setText(emptyLabel);
    table.setDragEnabled(false);
    table.setShowVerticalLines(false);

    TableColumn typeColumn = table.getColumnModel().getColumn(0);
    int width = table.getFontMetrics(table.getFont()).stringWidth("DIRECTORY") + 10;
    typeColumn.setPreferredWidth(width);
    typeColumn.setMaxWidth(width);
    typeColumn.setMinWidth(width);

    table.getTableHeader().setReorderingAllowed(false);
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent evt) {
        if (evt.getClickCount() == 2) {
          editEntry();
        }
      }
    });

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(table)
      .setEditActionName("Edit")
      .setEditAction(e -> editEntry())
      .setAddAction(new AddEntryAction())
      .setRemoveAction(new RemoveEntryAction())
      .disableUpDownActions();

    panel = new JPanel(new BorderLayout());
    panel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
  }

  public JPanel getComponent() {
    return panel;
  }

  public void set(List<ExclusionItem> data) {
    model.set(data);
  }

  public List<ExclusionItem> get() {
    return new ArrayList<>(model.items());
  }

  private void editEntry() {
    int selectedIndex = table.getSelectedRow();
    if (selectedIndex >= 0) {
      ExclusionItem value = model.items().get(selectedIndex);
      ExclusionItem newValue = onEdit.apply(value);
      if (newValue != null) {
        model.items().set(selectedIndex, newValue);
      }
    }
  }

  private class AddEntryAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      ExclusionItem input = onAdd.get();
      if (input != null) {
        model.items().add(input);
        int lastRow = model.getRowCount() - 1;
        table.setRowSelectionInterval(lastRow, lastRow);
      }
    }
  }

  private static class Model extends AbstractTableModel {
    private List<ExclusionItem> rows = new ArrayList<>();

    @Override public int getRowCount() {
      return rows.size();
    }

    @Override public int getColumnCount() {
      return 2;
    }

    public void add(ExclusionItem item) {
      int lastRow = rows.size();
      rows.add(item);
      fireTableRowsInserted(lastRow, lastRow);
    }

    public void remove(int index) {
      rows.remove(index);
      fireTableRowsDeleted(index, index);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }

    @Override
    public String getColumnName(int column) {
      return (column == 0) ? "Type" : "Item";
    }

    public void set(List<ExclusionItem> rows) {
      this.rows = rows;
      fireTableDataChanged();
    }

    public List<ExclusionItem> items() {
      return rows;
    }

    @Override public Object getValueAt(int rowIndex, int columnIndex) {
      ExclusionItem item = rows.get(rowIndex);
      if (columnIndex == 0) {
        return StringUtils.capitalize(item.type().name().toLowerCase(Locale.US));
      }
      return item.item();
    }
  }

  private class RemoveEntryAction implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton anActionButton) {
      int selectedIndex = table.getSelectedRow();

      ExclusionItem entry = model.items().get(selectedIndex);
      if (entry == null) {
        return;
      }
      model.remove(selectedIndex);
      if (model.getRowCount() > 0) {
        int newIndex = Math.min(model.getRowCount() - 1, Math.max(selectedIndex - 1, 0));
        table.setRowSelectionInterval(newIndex, newIndex);
      }
    }
  }

  public void add(ExclusionItem value) {
    model.add(value);
    int lastIndex = model.getRowCount() - 1;
    table.setRowSelectionInterval(lastIndex, lastIndex);
  }
}
