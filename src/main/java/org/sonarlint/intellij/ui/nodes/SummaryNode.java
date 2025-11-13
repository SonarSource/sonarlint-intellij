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
package org.sonarlint.intellij.ui.nodes;

import java.util.Collections;
import java.util.Comparator;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.ui.tree.TreeSummary;

public class SummaryNode extends AbstractNode {
  private final TreeSummary treeSummary;

  public SummaryNode(TreeSummary treeSummary) {
    this.treeSummary = treeSummary;
  }

  public int insertFileNode(FileNode newChild, Comparator<FileNode> comparator) {
    if (children == null) {
      insert(newChild, 0);
      return 0;
    }

    var nodes = children.stream().map(n -> (FileNode) n).toList();
    var foundIndex = Collections.binarySearch(nodes, newChild, comparator);
    if (foundIndex >= 0) {
      throw new IllegalArgumentException("Child already exists");
    }

    int insertIdx = -foundIndex - 1;
    insert(newChild, insertIdx);
    return insertIdx;
  }

  @Override
  public void render(TreeCellRenderer renderer) {
    renderer.append(treeSummary.getText());
    renderer.setToolTipText(null);
  }

  @Override
  public String toString() {
    return treeSummary.getText();
  }
}
