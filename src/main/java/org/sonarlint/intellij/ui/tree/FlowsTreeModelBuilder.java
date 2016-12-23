/*
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

import com.intellij.openapi.editor.RangeMarker;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultTreeModel;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.ui.nodes.LabelNode;
import org.sonarlint.intellij.ui.nodes.LocationNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;

public class FlowsTreeModelBuilder {
  private SummaryNode summary;
  private DefaultTreeModel model;

  public FlowsTreeModelBuilder() {
  }

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

  private boolean containsLocations(List<LiveIssue.Flow> flows) {
    return flows.stream().flatMap(f -> f.locations().stream()).findAny().isPresent();
  }

  public void setFlows(List<LiveIssue.Flow> flows, @Nullable RangeMarker rangeMarker, @Nullable String message) {
    if (rangeMarker == null || !containsLocations(flows)) {
      clearFlows();
      return;
    }

    summary = new SummaryNode();
    int i = 1;
    for (LiveIssue.Flow f : flows) {
      LabelNode label = new LabelNode("Flow " + i);
      summary.add(label);
      int j = 1;
      for (LiveIssue.IssueLocation location : f.locations()) {
        LocationNode locationNode = new LocationNode(j, location.location(), location.message());
        label.add(locationNode);
        j++;
      }
      label.add(new LocationNode(j, rangeMarker, message));
      i++;
    }
    model.setRoot(summary);
  }
}
