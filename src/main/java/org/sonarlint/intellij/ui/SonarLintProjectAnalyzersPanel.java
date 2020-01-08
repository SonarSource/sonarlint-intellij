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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;

public class SonarLintProjectAnalyzersPanel {
  private static final Logger LOGGER = Logger.getInstance(SonarLintProjectAnalyzersPanel.class);
  private final Project project;
  private JBTable analyzerTable;
  private JPanel panel;
  private JPanel tablePanel;
  private Model tableModel;

  public SonarLintProjectAnalyzersPanel(Project project) {
    this.project = project;
    reload();
  }

  public JPanel getPanel() {
    return panel;
  }

  public void reload() {
    ProjectBindingManager bindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    try {
      Collection<LoadedAnalyzer> loadedAnalyzers = bindingManager.getFacade().getLoadedAnalyzers();
      tableModel.set(loadedAnalyzers);
    } catch (Exception e) {
      LOGGER.error(e);
      tableModel.set(Collections.emptyList());
    }
  }

  private void createUIComponents() {
    tableModel = new Model();
    analyzerTable = new JBTable(tableModel);
    analyzerTable.setShowGrid(false);
    analyzerTable.setIntercellSpacing(JBUI.emptySize());
    analyzerTable.getEmptyText().setText("No information");
    analyzerTable.setDragEnabled(false);
    analyzerTable.setShowVerticalLines(false);
    analyzerTable.getTableHeader().setReorderingAllowed(false);
    analyzerTable.enableInputMethods(false);
    analyzerTable.setDefaultRenderer(String.class, new NoFocusCellRenderer(new DefaultTableCellRenderer()));

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(analyzerTable);
    tablePanel = decorator.createPanel();
  }

  private static class NoFocusCellRenderer extends DefaultTableCellRenderer {
    private final TableCellRenderer delegate;

    public NoFocusCellRenderer(TableCellRenderer delegate) {
      this.delegate = delegate;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return delegate.getTableCellRendererComponent(table, value, isSelected, false, row, column);
    }
  }

  private static class Model extends AbstractTableModel {
    private List<LoadedAnalyzer> rows = new ArrayList<>();

    @Override public int getRowCount() {
      return rows.size();
    }

    @Override public int getColumnCount() {
      return 2;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public String getColumnName(int column) {
      return (column == 0) ? "Code Analyzer" : "Version";
    }

    public void set(Collection<LoadedAnalyzer> rows) {
      this.rows = new ArrayList<>(rows);
      fireTableDataChanged();
    }

    public List<LoadedAnalyzer> items() {
      return rows;
    }

    @Override public Object getValueAt(int rowIndex, int columnIndex) {
      LoadedAnalyzer item = rows.get(rowIndex);
      if (columnIndex == 0) {
        return StringUtils.capitalize(item.name());
      }
      return item.version();
    }
  }
}


