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
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import javax.swing.tree.DefaultTreeModel;
import org.sonarlint.intellij.finding.Flow;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FlowNode;
import org.sonarlint.intellij.ui.nodes.FlowSecondaryLocationNode;
import org.sonarlint.intellij.ui.nodes.PrimaryLocationNode;

public class FlowsTreeModelBuilder {
  private FlowSummaryNode summary;
  private DefaultTreeModel model;

  public DefaultTreeModel createModel() {
    summary = new FlowSummaryNode();
    model = new DefaultTreeModel(summary);
    model.setRoot(summary);
    return model;
  }

  public void clearFlows() {
    summary = null;
    model.setRoot(null);
  }

  public void populateForFinding(LiveFinding finding) {
    var context = finding.context();
    if (context.isEmpty()) {
      clearFlows();
      return;
    }
    var findingContext = context.get();
    populateForFinding(finding.file(), finding.getRange(), finding.getMessage(), findingContext.flows());
  }

  public void populateForFinding(VirtualFile file, @Nullable RangeMarker rangeMarker, String summaryDescription, List<Flow> flows) {
    if (rangeMarker == null || flows.isEmpty()) {
      clearFlows();
      return;
    }
    if (flows.size() == 1) {
      setSingleFlow(file, flows.get(0), rangeMarker, summaryDescription);
    } else {
      setMultipleFlows(file, flows, rangeMarker, summaryDescription);
    }
  }

  private void setMultipleFlows(VirtualFile file, List<Flow> flows, RangeMarker rangeMarker, @Nullable String message) {
    summary = new FlowSummaryNode();
    var primaryLocationNode = new PrimaryLocationNode(file, rangeMarker, message, flows.get(0));
    summary.add(primaryLocationNode);

    var flowIndex = 1;
    for (var flow : flows) {
      var flowNode = new FlowNode(flow, "Flow " + flowIndex);
      primaryLocationNode.add(flowNode);

      var locationIndex = 1;
      for (var location : flow.getLocations()) {
        var locationNode = new FlowSecondaryLocationNode(locationIndex, location, flow);
        flowNode.add(locationNode);
        locationIndex++;
      }
      flowIndex++;
    }
    model.setRoot(summary);
  }

  private void setSingleFlow(VirtualFile file, Flow flow, RangeMarker rangeMarker, @Nullable String message) {
    summary = new FlowSummaryNode();
    var primaryLocation = new PrimaryLocationNode(file, rangeMarker, message, flow);
    primaryLocation.setBold(true);
    summary.add(primaryLocation);

    var locationIndex = 1;
    for (var location : flow.getLocations()) {
      var locationNode = new FlowSecondaryLocationNode(locationIndex, location, flow);
      primaryLocation.add(locationNode);
      locationIndex++;
    }

    model.setRoot(summary);
  }

  private static class FlowSummaryNode extends AbstractNode {
    @Override
    public void render(TreeCellRenderer renderer) {
      // nothing to show for the root
    }
  }
}
