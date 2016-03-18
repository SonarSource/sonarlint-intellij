/**
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
package org.sonarlint.intellij.ui.tree;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.ui.nodes.IssueNode;

/**
 * Extends {@link Tree} to provide context data for actions
 */
public class IssueTree extends Tree implements DataProvider {
  private final Project project;

  public IssueTree(Project project, TreeModel model) {
    super(model);
    this.project = project;
  }

  @Nullable @Override public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      TreePath path = getSelectionPath();
      if (path == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
      if (!(node instanceof IssueNode)) {
        return null;
      }
      IssuePointer issue = ((IssueNode) node).issue();
      int offset;

      RangeMarker range = issue.range();
      if (range != null) {
        offset = range.getStartOffset();
      } else {
        offset = 0;
      }
      return new OpenFileDescriptor(project, issue.psiFile().getVirtualFile(), offset);
    }

    return null;
  }
}
