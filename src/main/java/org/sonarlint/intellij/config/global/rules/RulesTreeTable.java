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
package org.sonarlint.intellij.config.global.rules;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.profile.codeInspection.ui.table.ThreeStateCheckBoxRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.table.TableColumn;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;

public class RulesTreeTable extends TreeTable {
  static final int TREE_COLUMN = 0;
  static final int IS_ENABLED_COLUMN = 1;
  private final RulesTreeTableModel treeTableModel;

  public RulesTreeTable(RulesTreeTableModel treeTableModel) {
    super(treeTableModel);
    this.treeTableModel = treeTableModel;

    setUpColumns();
    setListeners();

    getTableHeader().setReorderingAllowed(false);
    getEmptyText().setText("No rules available");
  }

  private void setListeners() {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        final TreePath path = getTree().getPathForRow(getTree().getLeadSelectionRow());
        if (path != null) {
          RulesTreeNode node = (RulesTreeNode) path.getLastPathComponent();
          if (node.isLeaf()) {
            treeTableModel.swapAndRefresh(node);
          }
        }
        return true;
      }
    }.installOn(this);

    registerKeyboardAction(e -> {
      final TreePath path = getTree().getPathForRow(getTree().getLeadSelectionRow());
      if (path != null) {
        treeTableModel.swapAndRefresh(path);
      }
      updateUI();
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
  }

  private void setUpColumns() {
    TableColumn isEnabledColumn = getColumnModel().getColumn(IS_ENABLED_COLUMN);
    isEnabledColumn.setMaxWidth(JBUI.scale(20 + getAdditionalPadding()));
    ThreeStateCheckBoxRenderer boxRenderer = new ThreeStateCheckBoxRenderer();
    boxRenderer.setOpaque(true);
    isEnabledColumn.setCellRenderer(boxRenderer);
    isEnabledColumn.setCellEditor(new ThreeStateCheckBoxRenderer());
  }

  private static int getAdditionalPadding() {
    return SystemInfo.isMac ? 10 : 0;
  }

  @Override
  public void paint(@NotNull Graphics g) {
    super.paint(g);
    UIUtil.fixOSXEditorBackground(this);
  }
}
