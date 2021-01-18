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
package org.sonarlint.intellij.ui.tree;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.issue.Location;
import org.sonarlint.intellij.ui.nodes.FlowNode;
import org.sonarlint.intellij.ui.nodes.FlowSecondaryLocationNode;
import org.sonarlint.intellij.ui.nodes.PrimaryLocationNode;
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
        DefaultMutableTreeNode selectedNode = getSelectedNode();
        if (selectedNode != null) {
          highlightInEditor(selectedNode);
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

    EditSourceOnDoubleClickHandler.install(this, () -> navigateToEditor(getSelectedNode()));
    EditSourceOnEnterKeyHandler.install(this, () -> navigateToEditor(getSelectedNode()));
  }

  public void expandAll() {
    for (int i = 0; i < getRowCount(); i++) {
      expandRow(i);
    }
  }

  private void highlightInEditor(DefaultMutableTreeNode node) {
    EditorDecorator highlighter = SonarLintUtils.getService(project, EditorDecorator.class);
    if (node instanceof FlowNode) {
      FlowNode flowNode = (FlowNode) node;
      highlighter.highlightFlow(flowNode.getFlow());
    } else if (node instanceof FlowSecondaryLocationNode) {
      FlowSecondaryLocationNode locationNode = (FlowSecondaryLocationNode) node;
      highlighter.highlightSecondaryLocation(locationNode.getSecondaryLocation(), locationNode.getAssociatedFlow());
    } else if (node instanceof PrimaryLocationNode) {
      PrimaryLocationNode primaryLocationNode = (PrimaryLocationNode) node;
      highlighter.highlightPrimaryLocation(primaryLocationNode.rangeMarker(), primaryLocationNode.message(), primaryLocationNode.getAssociatedFlow());
    }
  }

  private void navigateToEditor(@Nullable DefaultMutableTreeNode node) {
    if (node == null) {
      return;
    }
    RangeMarker rangeMarker = null;
    if (node instanceof FlowNode) {
      FlowNode flowNode = (FlowNode) node;
      rangeMarker = flowNode.getFlow().getLocations().stream().findFirst().map(Location::getRange).orElse(null);
    } else if (node instanceof PrimaryLocationNode) {
      PrimaryLocationNode locationNode = (PrimaryLocationNode) node;
      rangeMarker = locationNode.rangeMarker();
    }
    if (rangeMarker == null || !rangeMarker.isValid()) {
      return;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(rangeMarker.getDocument());
    if (psiFile != null && psiFile.isValid()) {
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
