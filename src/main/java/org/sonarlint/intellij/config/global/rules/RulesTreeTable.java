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
package org.sonarlint.intellij.config.global.rules;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ui.table.ThreeStateCheckBoxRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.table.IconTableCellRenderer;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.tree.TreePath;

public class RulesTreeTable extends TreeTable {
  static final int TREE_COLUMN = 0;
  static final int ICONS_COLUMN = 1;
  static final int IS_ENABLED_COLUMN = 2;
  private final RulesTreeTableModel treeTableModel;

  public RulesTreeTable(RulesTreeTableModel treeTableModel) {
    super(treeTableModel);
    this.treeTableModel = treeTableModel;

    setUpColumns();
    installListeners();

    getTableHeader().setReorderingAllowed(false);
    getEmptyText().setText("No rules available");
  }

  private void installListeners() {
    installDoubleClickListener();
    installKeyboardListener();
    installMouseListener();
  }

  private void installDoubleClickListener() {
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        final TreePath path = getTree().getPathForRow(getTree().getLeadSelectionRow());
        if (path != null) {
          var node = (RulesTreeNode) path.getLastPathComponent();
          if (node.isLeaf()) {
            treeTableModel.swapAndRefresh(node);
          }
        }
        return true;
      }
    }.installOn(this);
  }

  private void installKeyboardListener() {
    registerKeyboardAction(e -> {
      final var path = getTree().getPathForRow(getTree().getLeadSelectionRow());
      if (path != null) {
        treeTableModel.swapAndRefresh(path);
      }
      updateUI();
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
  }

  private void installMouseListener() {
    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(final MouseEvent e) {
        var point = e.getPoint();
        var column = columnAtPoint(point);
        if (column != ICONS_COLUMN) {
          return;
        }
        var row = rowAtPoint(point);
        final var path = getTree().getPathForRow(row);
        if (path == null) {
          return;
        }

        var obj = path.getLastPathComponent();

        if (obj instanceof RulesTreeNode.Rule rule && (rule.getHighestQuality() != null && rule.getHighestImpact() != null)) {
            var label = new JLabel();
            var qualityText = StringUtil.capitalize(rule.getHighestQuality().toString().toLowerCase(Locale.ENGLISH));
            var impactText = StringUtil.capitalize(rule.getHighestImpact().getLabel().toLowerCase(Locale.ENGLISH));
            var text = impactText + " impact on " + qualityText;
            label.setText(StringUtil.capitalize(text.replace('_', ' ').toLowerCase(Locale.ENGLISH)));
            IdeTooltipManager.getInstance().show(new IdeTooltip(RulesTreeTable.this, point, label), false);
        }
      }
    });
  }

  private void setUpColumns() {
    var isEnabledColumn = getColumnModel().getColumn(IS_ENABLED_COLUMN);
    isEnabledColumn.setMaxWidth(JBUI.scale(20 + getAdditionalPadding()));
    var boxRenderer = new ThreeStateCheckBoxRenderer();
    boxRenderer.setOpaque(true);
    isEnabledColumn.setCellRenderer(boxRenderer);
    isEnabledColumn.setCellEditor(new ThreeStateCheckBoxRenderer());

    var iconsColumn = getColumnModel().getColumn(ICONS_COLUMN);
    iconsColumn.setCellRenderer(new IconTableCellRenderer<Icon>() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
        var component = super.getTableCellRendererComponent(table, value, false, focus, row, column);
        var bgColor = selected ? table.getSelectionBackground() : table.getBackground();
        component.setBackground(bgColor);
        ((JLabel) component).setText("");
        return component;
      }

      @Override
      protected Icon getIcon(Icon value, JTable table, int row) {
        return value;
      }
    });

    iconsColumn.setMaxWidth(JBUI.scale(20));
  }

  private static int getAdditionalPadding() {
    return SystemInfo.isMac ? 10 : 0;
  }
}
