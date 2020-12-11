/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import javax.swing.tree.TreeNode;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

public class SummaryNode extends AbstractNode {
  private String emptyText;

  public SummaryNode() {
    super();
    this.emptyText = "No issues to display";
  }

  public void setEmptyText(String emptyText) {
    this.emptyText = emptyText;
  }

  public String getText() {
    int issues = getIssueCount();
    int files = getChildCount();

    if (issues == 0) {
      return emptyText;
    }

    return String.format("Found %d %s in %d %s", issues, issues == 1 ? "issue" : "issues", files, files == 1 ? "file" : "files");
  }

  public int getInsertIdx(FileNode newChild, Comparator<FileNode> comparator) {
    if (children == null) {
      insert(newChild, 0);
      return 0;
    }

    // keep the cast for Java 8 compat
    List<FileNode> nodes = ((Vector<TreeNode>)children).stream().map(FileNode.class::cast).collect(Collectors.<FileNode>toList());
    int i = Collections.binarySearch(nodes, newChild, comparator);
    if (i >= 0) {
      throw new IllegalArgumentException("Child already exists");
    }

    int insertIdx = -i - 1;
    insert(newChild, insertIdx);
    return insertIdx;
  }

  @Override public void render(TreeCellRenderer renderer) {
    renderer.append(getText());
  }
}
