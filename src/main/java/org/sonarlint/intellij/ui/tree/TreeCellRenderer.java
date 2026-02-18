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
package org.sonarlint.intellij.ui.tree;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RowIcon;
import java.awt.event.MouseEvent;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JTree;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.report.FindingSelectionManager;

/**
 * Can't unit test this because the parent uses a service, depending on a pico container with a method
 * that doesn't exist in the pico container used by SonarLint (different versions), causing NoSuchMethodError.
 */
public class TreeCellRenderer extends ColoredTreeCellRenderer {
  private final NodeRenderer<Object> nodeRenderer;
  @Nullable
  private FindingSelectionManager selectionManager;

  public TreeCellRenderer() {
    nodeRenderer = null;
  }

  public TreeCellRenderer(NodeRenderer<Object> nodeRenderer) {
    this.nodeRenderer = nodeRenderer;
  }

  public void setSelectionManager(@Nullable FindingSelectionManager manager) {
    this.selectionManager = manager;
  }

  private String iconToolTip = null;

  @Override
  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (nodeRenderer != null) {
      nodeRenderer.render(this, value);
    } else {
      var node = (AbstractNode) value;
      node.render(this);
    }

    if (selectionManager != null && value instanceof IssueNode issueNode) {
      boolean checked = selectionManager.isSelected(issueNode.issue().getId());
      Icon checkboxIcon = checked ? AllIcons.Diff.GutterCheckBoxSelected : AllIcons.Diff.GutterCheckBox;
      Icon existing = getIcon();
      if (existing != null) {
        RowIcon rowIcon = new RowIcon(2, RowIcon.Alignment.CENTER);
        rowIcon.setIcon(checkboxIcon, 0);
        rowIcon.setIcon(existing, 1);
        setIcon(rowIcon);
      } else {
        setIcon(checkboxIcon);
      }
    }
  }

  public void setIconToolTip(String toolTip) {
    this.iconToolTip = toolTip;
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    if (iconToolTip == null) {
      return super.getToolTipText(event);
    }

    if (event.getX() < getIconWidth()) {
      return iconToolTip;
    }

    return super.getToolTipText(event);
  }

  private int getIconWidth() {
    if (getIcon() != null) {
      return getIcon().getIconWidth() + myIconTextGap;
    }
    return 0;
  }
}
