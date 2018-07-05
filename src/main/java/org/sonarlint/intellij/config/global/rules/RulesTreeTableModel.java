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

import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import javax.swing.JTree;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import org.jetbrains.annotations.Nullable;

import static org.sonarlint.intellij.config.global.rules.RulesTreeTable.IS_ENABLED_COLUMN;
import static org.sonarlint.intellij.config.global.rules.RulesTreeTable.TREE_COLUMN;

public class RulesTreeTableModel extends DefaultTreeModel implements TreeTableModel {
  private TreeTable treeTable;

  public RulesTreeTableModel(TreeNode root) {
    super(root);
  }

  @Override public int getColumnCount() {
    return 2;
  }

  @Nullable
  @Override
  public String getColumnName(int column) {
    return null;
  }

  @Override
  public Class getColumnClass(int column) {
    switch (column) {
      case TREE_COLUMN:
        return TreeTableModel.class;
      case IS_ENABLED_COLUMN:
        return Boolean.class;
    }
    throw new IllegalArgumentException();
  }

  @Nullable
  @Override
  public Object getValueAt(final Object node, final int column) {
    if (column == TREE_COLUMN) {
      return null;
    }
    if (column == IS_ENABLED_COLUMN) {
      RulesTreeNode rule = (RulesTreeNode) node;
      return rule.isActivated();
    }

    throw new IllegalArgumentException();
  }

  @Override
  public boolean isCellEditable(final Object node, final int column) {
    return column == IS_ENABLED_COLUMN;
  }

  @Override public void setValueAt(Object aValue, Object node, int column) {
    if (column == IS_ENABLED_COLUMN) {
      boolean value = (boolean) aValue;
      if (node instanceof RulesTreeNode.Rule) {
        RulesTreeNode.Rule rule = (RulesTreeNode.Rule) node;
        activateRule(rule, value);
      } else if (node instanceof RulesTreeNode.Language) {
        RulesTreeNode.Language lang = (RulesTreeNode.Language) node;
        activateLanguage(lang, value);
      }

      ((AbstractTableModel) treeTable.getModel()).fireTableDataChanged();
    }
  }

  private void activateRule(RulesTreeNode.Rule rule, boolean activate) {
    rule.setIsActivated(activate);
    RulesTreeNode.Language lang = (RulesTreeNode.Language) rule.getParent();
    refreshLanguageActivation(lang);
  }

  private static void refreshLanguageActivation(RulesTreeNode.Language lang) {
    boolean seenActive = false;
    boolean seenInactive = false;
    boolean isChanged = false;

    for (RulesTreeNode.Rule rule : lang.childrenIterable()) {
      if (rule.isChanged()) {
        isChanged = true;
      }
      if (rule.isActivated()) {
        seenActive = true;
      } else {
        seenInactive = true;
      }
    }

    if (seenActive) {
      if (seenInactive) {
        lang.setIsActivated(null);
      } else {
        lang.setIsActivated(true);
      }
    } else if (seenInactive) {
      lang.setIsActivated(false);
    }
    lang.setIsChanged(isChanged);
  }

  private static void activateLanguage(RulesTreeNode.Language lang, boolean activate) {
    lang.setIsActivated(activate);
    for (RulesTreeNode.Rule rule : lang.childrenIterable()) {
      rule.setIsActivated(activate);
    }
  }

  public void restoreDefaults() {
    RulesTreeNode.Root rootNode = (RulesTreeNode.Root) getRoot();
    for (RulesTreeNode.Language lang : rootNode.childrenIterable()) {
      for (RulesTreeNode.Rule rule : lang.childrenIterable()) {
        rule.setIsActivated(rule.getDefaultActivation());
      }
      refreshLanguageActivation(lang);
    }
    ((AbstractTableModel) treeTable.getModel()).fireTableDataChanged();
  }

  public void swapAndRefresh(Object node) {
    if (node instanceof RulesTreeNode.Rule) {
      RulesTreeNode.Rule rule = (RulesTreeNode.Rule) node;
      activateRule(rule, !rule.isActivated());
    } else if (node instanceof RulesTreeNode.Language) {
      RulesTreeNode.Language lang = (RulesTreeNode.Language) node;
      activateLanguage(lang, lang.isActivated() == null || !lang.isActivated());
    }
    ((AbstractTableModel) treeTable.getModel()).fireTableDataChanged();
  }

  @Override public void setTree(JTree tree) {
    treeTable = ((TreeTableTree) tree).getTreeTable();
  }
}
