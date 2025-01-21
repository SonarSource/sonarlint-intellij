/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import com.intellij.openapi.project.Project;
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
import javax.swing.tree.TreeSelectionModel;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.finding.Location;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FlowNode;
import org.sonarlint.intellij.ui.nodes.FlowSecondaryLocationNode;
import org.sonarlint.intellij.ui.nodes.PrimaryLocationNode;

import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

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
        var selectedNode = getSelectedNode();
        if (selectedNode != null) {
          highlightInEditor(selectedNode);
        }
      }
    });
    var listener = new TreeWillExpandListener() {
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
    addTreeWillExpandListener(listener);
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    EditSourceOnDoubleClickHandler.install(this, () -> navigateToEditor(getSelectedNode()));
    EditSourceOnEnterKeyHandler.install(this, () -> navigateToEditor(getSelectedNode()));
  }

  public void expandAll() {
    runOnUiThread(project, () -> {
      for (var i = 0; i < getRowCount(); i++) {
        expandRow(i);
      }
    });
  }

  private void highlightInEditor(DefaultMutableTreeNode node) {
    var highlighter = SonarLintUtils.getService(project, EditorDecorator.class);
    if (node instanceof FlowNode flowNode) {
      highlighter.highlightFlow(flowNode.getFlow());
    } else if (node instanceof FlowSecondaryLocationNode locationNode) {
      highlighter.highlightSecondaryLocation(locationNode.getSecondaryLocation(), locationNode.getAssociatedFlow());
    } else if (node instanceof PrimaryLocationNode primaryLocationNode) {
      highlighter.highlightPrimaryLocation(primaryLocationNode.rangeMarker(), primaryLocationNode.message(), primaryLocationNode.getAssociatedFlow());
    }
  }

  private void navigateToEditor(@Nullable AbstractNode node) {
    if (node == null) {
      return;
    }
    RangeMarker rangeMarker;
    if (node instanceof FlowNode flowNode) {
      rangeMarker = flowNode.getFlow().getLocations().stream().findFirst().map(Location::getRange).orElse(null);
    } else if (node instanceof PrimaryLocationNode locationNode) {
      rangeMarker = locationNode.rangeMarker();
    } else {
      rangeMarker = null;
    }

    node.openFileFromRangeMarker(project, rangeMarker);
  }

  @CheckForNull
  private AbstractNode getSelectedNode() {
    var path = getSelectionPath();
    if (path == null) {
      return null;
    }
    return (AbstractNode) path.getLastPathComponent();
  }
}
