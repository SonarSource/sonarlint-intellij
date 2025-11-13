/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;

public class SonarLintProjectPropertiesPanel {
  private PropertiesTableModel tableModel;

  public Map<String, String> getProperties() {
    return tableModel.getOptions();
  }

  public void setAnalysisProperties(Map<String, String> props) {
    tableModel.setOptions(props);
    tableModel.fireTableDataChanged();
  }

  public JPanel create() {
    tableModel = new PropertiesTableModel();
    // Unfortunately TableModel's listener does not work properly, it doesn't receive events related to changed cells.
    final var table = new JBTable(tableModel);
    table.getEmptyText().setText("No SonarQube for IDE properties configured for this project");

    var tablePanel = ToolbarDecorator.createDecorator(table)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(anActionButton -> {
        final var cellEditor = table.getCellEditor();
        if (cellEditor != null) {
          cellEditor.stopCellEditing();
        }
        final var model = table.getModel();
        ((EditableModel) model).addRow();
        TableUtil.editCellAt(table, model.getRowCount() - 1, 0);
      })
      .createPanel();

    tablePanel.setBorder(BorderFactory.createTitledBorder("Analysis parameters"));
    return tablePanel;
  }

  public boolean isModified(SonarLintProjectSettings projectSettings) {
    return !getProperties().equals(projectSettings.getAdditionalProperties());
  }

  private static class PropertiesTableModel extends AbstractTableModel implements EditableModel {
    private final List<KeyValuePair> myRows = new ArrayList<>();

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case 0 -> "Property Name";
        case 1 -> "Value";
        default -> super.getColumnName(column);
      };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public int getRowCount() {
      return myRows.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0 || columnIndex == 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return switch (columnIndex) {
        case 0 -> myRows.get(rowIndex).key;
        case 1 -> myRows.get(rowIndex).value;
        default -> null;
      };
    }

    @Override
    public void setValueAt(@Nullable Object aValue, int rowIndex, int columnIndex) {
      if (aValue != null) {
        if (columnIndex == 0) {
          myRows.get(rowIndex).key = (String) aValue;
        } else if (columnIndex == 1) {
          myRows.get(rowIndex).value = (String) aValue;
        }
      }
    }

    @Override
    public void removeRow(int idx) {
      myRows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      // Unsupported
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return false;
    }

    @Override
    public void addRow() {
      myRows.add(new KeyValuePair());
      final var index = myRows.size() - 1;
      fireTableRowsInserted(index, index);
    }

    public void clear() {
      final var count = myRows.size();
      if (count > 0) {
        myRows.clear();
        fireTableRowsDeleted(0, count - 1);
      }
    }

    public Map<String, String> getOptions() {
      return myRows.stream()
        .map(p -> Map.entry(p.key.trim(), p.value.trim()))
        .filter(p -> !"".equals(p.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void setOptions(Map<String, String> options) {
      clear();
      if (!options.isEmpty()) {
        myRows.addAll(options.entrySet().stream()
          .map(e -> new KeyValuePair(e.getKey(), e.getValue()))
          .toList());
        myRows.sort(new KeyValueComparator());
        fireTableRowsInserted(0, options.size() - 1);
      }
    }

    private static class KeyValueComparator implements Comparator<KeyValuePair> {
      @Override
      public int compare(KeyValuePair o1, KeyValuePair o2) {
        return o1.key.compareToIgnoreCase(o2.key);
      }
    }

    private static final class KeyValuePair {
      String key;
      String value;

      KeyValuePair() {
        this("", "");
      }

      KeyValuePair(String key, String value) {
        this.key = key;
        this.value = value;
      }
    }
  }

}
