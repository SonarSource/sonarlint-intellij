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
  private final transient Supplier<String> filterSupplier;

  public RulesTreeTableRenderer(Supplier<String> filterSupplier) {
    this.filterSupplier = filterSupplier;
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
    boolean hasFocus) {
    var component = new SimpleColoredComponent();
    var node = (RulesTreeNode) value;
    var background = selected ? getSelectedBackgroundColor(tree) : UIUtil.getTreeBackground();
    UIUtil.changeBackGround(component, background);
    var foreground = selected ? UIUtil.getTreeSelectionForeground(true) : getUnselectedForegroundColor(node);

    String text = null;
    var style = SimpleTextAttributes.STYLE_PLAIN;

    if (value instanceof RulesTreeNode.LanguageNode) {
      style = SimpleTextAttributes.STYLE_BOLD;
      text = node.toString();
    } else if (value instanceof RulesTreeNode.Rule rule) {
      text = rule.getName();
    }

    if (text != null) {
      SearchUtil.appendFragments(filterSupplier.get(), text, style, foreground, background, component);
    }
    component.setForeground(foreground);
    return component;
  }

  private static Color getUnselectedForegroundColor(RulesTreeNode node) {
    return node.isNonDefault() ? PlatformColors.BLUE : UIUtil.getTreeForeground();
  }

  private static Color getSelectedBackgroundColor(JTree tree) {
    var reallyHasFocus = ((TreeTableTree) tree).getTreeTable().hasFocus();
    return UIUtil.getTreeSelectionBackground(reallyHasFocus);
  }
}
