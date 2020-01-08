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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.wm.impl.welcomeScreen.BottomLineBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

public class LabelNode extends AbstractNode {
  private final String label;

  public LabelNode(String label) {
    this.label = label;
  }

  @Override public void render(TreeCellRenderer renderer) {
    renderer.setIpad(JBUI.insets(3, 3, 3, 3));
    renderer.setBorder(new BottomLineBorder());
    renderer.append(label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true);
  }
}
