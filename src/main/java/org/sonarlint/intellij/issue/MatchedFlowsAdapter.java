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
package org.sonarlint.intellij.issue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

public class MatchedFlowsAdapter {
  public static Optional<IssueContext> adapt(List<LiveIssue.Flow> flows) {
    return flows.isEmpty()
      ? Optional.empty()
      : Optional.of(new IssueContext(adaptFlows(flows)));
  }

  private static List<LiveIssue.Flow> adaptFlows(List<LiveIssue.Flow> flows) {
    return flows.stream().anyMatch(LiveIssue.Flow::hasMoreThanOneLocation)
      ? reverse(flows)
      : singletonList(groupToSingleFlow(flows));
  }

  private static LiveIssue.Flow groupToSingleFlow(List<LiveIssue.Flow> flows) {
    return new LiveIssue.Flow(flows.stream()
      .flatMap(f -> f.locations().stream())
      .sorted(Comparator.comparing(i -> i.location().getStartOffset()))
      .collect(Collectors.toList()));
  }

  private static List<LiveIssue.Flow> reverse(List<LiveIssue.Flow> flows) {
    return flows.stream().map(f -> {
      ArrayList<LiveIssue.SecondaryLocation> reorderedLocations = new ArrayList<>(f.locations());
      Collections.reverse(reorderedLocations);
      return new LiveIssue.Flow(reorderedLocations);
    }).collect(Collectors.toList());
  }

  private MatchedFlowsAdapter() {
    // util class
  }
}
