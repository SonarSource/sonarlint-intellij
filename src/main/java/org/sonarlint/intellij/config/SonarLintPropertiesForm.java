package org.sonarlint.intellij.config;

import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;


public class SonarLintPropertiesForm {
  private JPanel rootPane;
  private JBTable propertyTable;

  public JPanel getRootPane() {
    return rootPane;
  }

  public SonarLintPropertiesForm(TableModel model) {
    rootPane = new JPanel(new BorderLayout());
    rootPane.setBorder(IdeBorderFactory.createTitledBorder("Annotation Processor options", false));
    propertyTable = new JBTable(model);
    propertyTable.getEmptyText().setText("No SonarLint property configured");
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
            ((EditableModel)model).addRow();
            TableUtil.editCellAt(table, model.getRowCount() - 1, 0);
          }
        })
        .createPanel();
  }
}
