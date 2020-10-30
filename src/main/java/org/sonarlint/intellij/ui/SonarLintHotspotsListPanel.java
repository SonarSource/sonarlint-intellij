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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.sonarlint.intellij.editor.SonarLintHighlighting;
import org.sonarlint.intellij.issue.hotspot.LocalHotspot;
import org.sonarlint.intellij.ui.nodes.HotspotNode;

import javax.swing.JComponent;
import javax.swing.tree.DefaultTreeModel;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintHotspotsListPanel {
  private final JPanel mainPanel;
  private final Tree hotspotsTree;
  private final DefaultTreeModel treeModel;
  private final Project project;
  private HotspotNode hotspotNode;

  public SonarLintHotspotsListPanel(Project project) {
    this.project = project;
    treeModel = new DefaultTreeModel(null);
    hotspotsTree = new Tree(treeModel);
    hotspotsTree.setCellRenderer(new HotspotCellRenderer());
    hotspotsTree.getEmptyText().setText("No hotspots to display, open one from SonarQube");
    mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(ScrollPaneFactory.createScrollPane(hotspotsTree), BorderLayout.CENTER);
    EditSourceOnDoubleClickHandler.install(hotspotsTree, this::navigateToLocation);
    EditSourceOnEnterKeyHandler.install(hotspotsTree, this::navigateToLocation);
  }

  public void setHotspot(LocalHotspot hotspot) {
    hotspotNode = new HotspotNode(hotspot);
    treeModel.setRoot(hotspotNode);
    hotspotsTree.addTreeSelectionListener(treeSelectionEvent ->
      SonarLintUtils.getService(project, SonarLintHighlighting.class).highlight(hotspotNode.getHotspot())
    );
  }

  public JComponent getPanel() {
    return mainPanel;
  }

  private void navigateToLocation() {
    if (hotspotNode == null || !hotspotNode.getHotspot().isValid()) {
      return;
    }

    int offset;
    RangeMarker range = hotspotNode.getHotspot().primaryLocation.range;
    if (range != null) {
      offset = range.getStartOffset();
    } else {
      offset = 0;
    }
    new OpenFileDescriptor(project, hotspotNode.getHotspot().primaryLocation.file, offset).navigate(true);
  }

}
