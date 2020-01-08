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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

  private static boolean containsLocations(List<LiveIssue.Flow> flows) {
    return flows.stream().flatMap(f -> f.locations().stream()).findAny().isPresent();
  }

  public void setFlows(List<LiveIssue.Flow> flows, @Nullable RangeMarker rangeMarker, @Nullable String message) {
    if (rangeMarker == null || !containsLocations(flows)) {
      clearFlows();
      return;
    }

    if (flows.size() == 1) {
      setSingleFlow(flows.iterator().next(), rangeMarker, message);
    } else if (flows.stream().noneMatch(flow -> flow.locations().size() != 1)) {
      setFlatList(flows, rangeMarker, message);
    } else {
      setMultipleFlows(flows, rangeMarker, message);
    }
  }

  private void setMultipleFlows(List<LiveIssue.Flow> flows, RangeMarker rangeMarker, @Nullable String message) {
    summary = new SummaryNode();
    LocationNode primaryLocation = new LocationNode(rangeMarker, message);
    summary.add(primaryLocation);

    int i = 1;
    for (LiveIssue.Flow f : flows) {
      LabelNode label = new LabelNode("Flow " + i);
      primaryLocation.add(label);

      List<LiveIssue.IssueLocation> reversedLocations = new ArrayList<>(f.locations());
      Collections.reverse(reversedLocations);
      int j = 1;
      for (LiveIssue.IssueLocation location : reversedLocations) {
        LocationNode locationNode = new LocationNode(j, location.location(), location.message());
        label.add(locationNode);
        j++;
      }
      i++;
    }
    model.setRoot(summary);
  }

  private void setFlatList(List<LiveIssue.Flow> flows, RangeMarker rangeMarker, @Nullable String message) {
    summary = new SummaryNode();
    LocationNode primaryLocation = new LocationNode(rangeMarker, message);
    primaryLocation.setBold(true);
    summary.add(primaryLocation);

    flows.stream()
      .flatMap(flow -> flow.locations().stream())
      .sorted(Comparator.comparing(i -> i.location().getStartOffset()))
      .forEachOrdered(location -> {
        LocationNode locationNode = new LocationNode(location.location(), location.message());
        primaryLocation.add(locationNode);
      });

    model.setRoot(summary);
  }

  private void setSingleFlow(LiveIssue.Flow flow, RangeMarker rangeMarker, @Nullable String message) {
    summary = new SummaryNode();
    LocationNode primaryLocation = new LocationNode(rangeMarker, message);
    primaryLocation.setBold(true);
    summary.add(primaryLocation);

    List<LiveIssue.IssueLocation> reversedLocations = new ArrayList<>(flow.locations());
    Collections.reverse(reversedLocations);

    int i = 1;
    for (LiveIssue.IssueLocation location : reversedLocations) {
      LocationNode locationNode = new LocationNode(i++, location.location(), location.message());
      primaryLocation.add(locationNode);
    }

    model.setRoot(summary);
  }
}
