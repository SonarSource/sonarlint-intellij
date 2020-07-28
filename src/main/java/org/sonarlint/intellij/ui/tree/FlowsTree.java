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
import javax.swing.tree.TreeSelectionModel;
import org.sonarlint.intellij.editor.SonarLintHighlighting;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.ui.nodes.FlowNode;
import org.sonarlint.intellij.ui.nodes.LocationNode;
import org.sonarlint.intellij.util.SonarLintUtils;

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
          navigateToSelected();
        }
      }
    });
    TreeWillExpandListener l = new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) {
        // expansion is always allowed
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        if (!(event.getPath().getLastPathComponent() instanceof FlowNode)) {
          throw new ExpandVetoException(event);
        }
      }
    };
    addTreeWillExpandListener(l);
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
  }

  public void expandAll() {
    for (int i = 0; i < getRowCount(); i++) {
      expandRow(i);
    }
  }

  private void navigateToSelected() {
    DefaultMutableTreeNode node = getSelectedNode();
    if (node == null) {
      return;
    }
    RangeMarker rangeMarker = null;
    SonarLintHighlighting highlighter = SonarLintUtils.getService(project, SonarLintHighlighting.class);
    if (node instanceof FlowNode) {
      FlowNode flowNode = (FlowNode) node;
      rangeMarker = flowNode.getFlow().locations().stream().findFirst().map(LiveIssue.IssueLocation::location).orElse(null);
      highlighter.highlightFlow(flowNode.getFlow());
    } else if (node instanceof LocationNode) {
      LocationNode locationNode = (LocationNode) node;
      rangeMarker = locationNode.rangeMarker();
      highlighter.highlightLocation(rangeMarker, locationNode.message());
    }

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
