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
package org.sonarlint.intellij.config.global.rules;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Component;
import java.util.function.Supplier;
import javax.swing.JTree;
import org.jdesktop.swingx.renderer.DefaultTreeRenderer;

public class RulesTreeTableRenderer extends DefaultTreeRenderer {
  private final Supplier<String> filterSupplier;

  public RulesTreeTableRenderer(Supplier<String> filterSupplier) {
    this.filterSupplier = filterSupplier;
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
    boolean hasFocus) {
    SimpleColoredComponent component = new SimpleColoredComponent();
    RulesTreeNode node = (RulesTreeNode) value;
    Color background = selected ? getSelectedBackgroundColor(tree) : UIUtil.getTreeTextBackground();
    UIUtil.changeBackGround(component, background);
    Color foreground = selected ? UIUtil.getTreeSelectionForeground() : getUnselectedForegroundColor(node);

    String text = null;
    int style = 0;

    if (value instanceof RulesTreeNode.Language) {
      style = SimpleTextAttributes.STYLE_BOLD;
      text = node.toString();
    } else if (value instanceof RulesTreeNode.Rule) {
      RulesTreeNode.Rule rule = (RulesTreeNode.Rule) value;
      text = rule.getName();
      style = SimpleTextAttributes.STYLE_PLAIN;
    }

    if (text != null) {
      SearchUtil.appendFragments(filterSupplier.get(), text, style, foreground, background, component);
    }
    component.setForeground(foreground);
    return component;
  }

  private static Color getUnselectedForegroundColor(RulesTreeNode node) {
    return node.isChanged() ? PlatformColors.BLUE : UIUtil.getTreeTextForeground();
  }

  private static Color getSelectedBackgroundColor(JTree tree) {
    boolean reallyHasFocus = ((TreeTableTree) tree).getTreeTable().hasFocus();
    return reallyHasFocus ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeUnfocusedSelectionBackground();
  }
}
