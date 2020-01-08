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

import com.intellij.util.ui.UIUtil;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

public abstract class AbstractNode<T extends AbstractNode> extends DefaultMutableTreeNode {
  protected int issueCount;
  protected int fileCount;

  public abstract void render(TreeCellRenderer renderer);

  public int getIssueCount() {
    if (issueCount < 0) {
      issueCount = 0;
      Enumeration children = super.children();

      while (children.hasMoreElements()) {
        AbstractNode node = (AbstractNode) children.nextElement();
        if (node == null) {
          continue;
        }

        issueCount += node.getIssueCount();
      }
    }

    return issueCount;
  }

  @Override
  public void remove(int index) {
    setDirty();
    super.remove(index);
  }

  @Override
  public void remove(MutableTreeNode aChild) {
    setDirty();
    super.remove(aChild);
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    setDirty();
    super.insert(newChild, childIndex);
  }

  @Override
  public void add(MutableTreeNode newChild) {
    setDirty();
    super.add(newChild);
  }

  public int getInsertIdx(T newChild, Comparator<? super TreeNode> comparator) {
    if (children == null) {
      insert(newChild, 0);
      return 0;
    }
    int i = Collections.binarySearch(children, newChild, comparator);
    if (i >= 0) {
      throw new IllegalArgumentException("Child already exists");
    }

    int insertIdx = -i - 1;
    insert(newChild, insertIdx);
    return insertIdx;
  }

  public int getFileCount() {
    if (fileCount < 0) {
      fileCount = 0;
      Enumeration children = super.children();

      while (children.hasMoreElements()) {
        AbstractNode node = (AbstractNode) children.nextElement();
        if (node == null) {
          continue;
        }

        fileCount += node.getFileCount();
      }
    }

    return fileCount;
  }

  public void setDirty() {
    fileCount = -1;
    issueCount = -1;
    if (super.getParent() != null) {
      ((AbstractNode) super.getParent()).setDirty();
    }
  }

  @NotNull
  protected static String spaceAndThinSpace() {
    String thinSpace = UIUtil.getLabelFont().canDisplay('\u2009') ? String.valueOf('\u2009') : " ";
    return " " + thinSpace;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "@" + hashCode();
  }
}
