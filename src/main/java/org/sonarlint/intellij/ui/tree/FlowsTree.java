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
package org.sonarlint.intellij.ui.tree;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.treeStructure.Tree;
import javax.annotation.CheckForNull;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.sonarlint.intellij.ui.nodes.LocationNode;

public class FlowsTree extends Tree {
  private final Project project;

  public FlowsTree(Project project, TreeModel model) {
    super(model);
    this.project = project;
    init();
  }

  private void init() {
    setRootVisible(false);
    setShowsRootHandles(false);
    setCellRenderer(new TreeCellRenderer());
    this.selectionModel.addTreeSelectionListener(e -> {
      if (e.getSource() != null) {
        TreePath newPath = e.getNewLeadSelectionPath();
        if (newPath != null) {
          Object o = newPath.getLastPathComponent();
          if (!(o instanceof LocationNode)) {
            FlowsTree.this.setSelectionPath(e.getOldLeadSelectionPath());
          } else {
            navigateToSelected();
          }
        }
      }
    });
    TreeWillExpandListener l = new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        // nothing to do
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        throw new ExpandVetoException(event);
      }
    };
    addTreeWillExpandListener(l);
  }

  public void expandAll() {
    for (int i = 0; i < getRowCount(); i++) {
      expandRow(i);
    }
  }

  private void navigateToSelected() {
    DefaultMutableTreeNode node = getSelectedNode();
    if (!(node instanceof LocationNode)) {
      return;
    }
    RangeMarker rangeMarker = ((LocationNode) node).rangeMarker();
    if (rangeMarker == null || !rangeMarker.isValid()) {
      return;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(rangeMarker.getDocument());
    if (psiFile != null) {
      new OpenFileDescriptor(project, psiFile.getVirtualFile(), rangeMarker.getStartOffset()).navigate(false);
    }
  }

  @CheckForNull
  private DefaultMutableTreeNode getSelectedNode() {
    TreePath path = getSelectionPath();
    if (path == null) {
      return null;
    }
    return (DefaultMutableTreeNode) path.getLastPathComponent();
  }
}
