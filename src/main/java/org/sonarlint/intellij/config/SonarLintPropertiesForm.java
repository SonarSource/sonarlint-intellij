/**
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
package org.sonarlint.intellij.config;

import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;

public class SonarLintPropertiesForm {
  private JPanel rootPane;
  private JBTable propertyTable;

  public SonarLintPropertiesForm(TableModel model) {
    rootPane = new JPanel(new BorderLayout());
    rootPane.setBorder(IdeBorderFactory.createTitledBorder("Annotation Processor options", false));
    propertyTable = new JBTable(model);
    propertyTable.getEmptyText().setText("No SonarLint properties configured");
    JPanel myOptionsPanel = createTablePanel(propertyTable);
    rootPane.add(myOptionsPanel, BorderLayout.CENTER);
  }

  private static JPanel createTablePanel(final JBTable table) {
    return ToolbarDecorator.createDecorator(table)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          final TableCellEditor cellEditor = table.getCellEditor();
          if (cellEditor != null) {
            cellEditor.stopCellEditing();
          }
          final TableModel model = table.getModel();
          ((EditableModel) model).addRow();
          TableUtil.editCellAt(table, model.getRowCount() - 1, 0);
        }
      })
      .createPanel();
  }

  public JPanel getRootPane() {
    return rootPane;
  }
}
