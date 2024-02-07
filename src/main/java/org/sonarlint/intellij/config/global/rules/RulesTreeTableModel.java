/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.util.CompoundIcon;

import static org.sonarlint.intellij.config.global.rules.RulesTreeTable.ICONS_COLUMN;
import static org.sonarlint.intellij.config.global.rules.RulesTreeTable.IS_ENABLED_COLUMN;
import static org.sonarlint.intellij.config.global.rules.RulesTreeTable.TREE_COLUMN;

public class RulesTreeTableModel extends DefaultTreeModel implements TreeTableModel {
  private TreeTable treeTable;

  public RulesTreeTableModel(TreeNode root) {
    super(root);
  }

  @Override
  public int getColumnCount() {
    return 3;
  }

  @Nullable
  @Override
  public String getColumnName(int column) {
    return null;
  }

  @Override
  public Class getColumnClass(int column) {
    return switch (column) {
      case TREE_COLUMN -> TreeTableModel.class;
      case ICONS_COLUMN -> Icon.class;
      case IS_ENABLED_COLUMN -> Boolean.class;
      default -> throw new IllegalArgumentException();
    };
  }

  @Nullable
  @Override
  public Object getValueAt(final Object node, final int column) {
    if (column == TREE_COLUMN) {
      return null;
    }

    if (column == ICONS_COLUMN) {
      if (node instanceof RulesTreeNode.Rule rule) {
        var gap = JBUIScale.isUsrHiDPI() ? 8 : 4;
        // Rules should always have the new CCT
        return rule.getHighestImpact() != null ? new CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, SonarLintIcons.impact(rule.getHighestImpact())) : null;
      }
      return null;
    }

    if (column == IS_ENABLED_COLUMN) {
      var treeNode = (RulesTreeNode) node;
      return treeNode.isActivated();
    }

    throw new IllegalArgumentException();
  }

  @Override
  public boolean isCellEditable(final Object node, final int column) {
    return column == IS_ENABLED_COLUMN;
  }

  @Override
  public void setValueAt(Object aValue, Object node, int column) {
    if (column == IS_ENABLED_COLUMN) {
      var value = (boolean) aValue;
      if (node instanceof RulesTreeNode.Rule rule) {
        activateRule(rule, value);
      } else if (node instanceof RulesTreeNode.Language lang) {
        activateLanguage(lang, value);
      }
      nodeChanged((RulesTreeNode) node);
    }
  }

  private void activateRule(RulesTreeNode.Rule rule, boolean activate) {
    rule.setIsActivated(activate);
    var lang = (RulesTreeNode.Language) rule.getParent();
    refreshLanguageActivation(lang);
  }

  void refreshLanguageActivation(RulesTreeNode.Language lang) {
    var seenActive = false;
    var seenInactive = false;
    var isChanged = false;

    for (var rule : lang.childrenIterable()) {
      if (rule.isNonDefault()) {
        isChanged = true;
      }
      if (Boolean.TRUE.equals(rule.isActivated())) {
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
    lang.setIsNonDefault(isChanged);
  }

  private void activateLanguage(RulesTreeNode.Language lang, boolean activate) {
    lang.setIsActivated(activate);
    for (var rule : lang.childrenIterable()) {
      rule.setIsActivated(activate);
    }
    refreshLanguageActivation(lang);
  }

  public void swapAndRefresh(Object node) {
    if (node instanceof RulesTreeNode.Rule rule) {
      activateRule(rule, !rule.isActivated());
    } else if (node instanceof RulesTreeNode.Language lang) {
      activateLanguage(lang, lang.isActivated() == null || !lang.isActivated());
    }
    nodeChanged((RulesTreeNode) node);
  }

  @Override
  public void setTree(JTree tree) {
    treeTable = ((TreeTableTree) tree).getTreeTable();
  }
}
