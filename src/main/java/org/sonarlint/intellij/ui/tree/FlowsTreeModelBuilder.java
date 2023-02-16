/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultTreeModel;
import org.sonarlint.intellij.finding.Flow;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.ui.nodes.FlowNode;
import org.sonarlint.intellij.ui.nodes.FlowSecondaryLocationNode;
import org.sonarlint.intellij.ui.nodes.PrimaryLocationNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;

public class FlowsTreeModelBuilder {
  private SummaryNode summary;
  private DefaultTreeModel model;

  public DefaultTreeModel createModel() {
    summary = new SummaryNode();
    model = new DefaultTreeModel(summary);
    model.setRoot(summary);
    return model;
  }

  public void clearFlows() {
    summary = null;
    model.setRoot(null);
  }

  public void populateForFinding(LiveFinding finding) {
    var rangeMarker = finding.getRange();
    var context = finding.context();
    if (rangeMarker == null || context.isEmpty()) {
      clearFlows();
      return;
    }
    var findingContext = context.get();
    var message = finding.getMessage();
    if (findingContext.hasUniqueFlow()) {
      setSingleFlow(findingContext.flows().get(0), rangeMarker, message);
    } else {
      setMultipleFlows(findingContext.flows(), rangeMarker, message);
    }
  }

  private void setMultipleFlows(List<Flow> flows, RangeMarker rangeMarker, @Nullable String message) {
    summary = new SummaryNode();
    var primaryLocationNode = new PrimaryLocationNode(rangeMarker, message, flows.get(0));
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

  private void setSingleFlow(Flow flow, RangeMarker rangeMarker, @Nullable String message) {
    summary = new SummaryNode();
    var primaryLocation = new PrimaryLocationNode(rangeMarker, message, flow);
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
}
