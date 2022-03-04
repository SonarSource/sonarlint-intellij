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
package org.sonarlint.intellij.issue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

public class MatchedFlowsAdapter {
  public static Optional<IssueContext> adapt(List<Flow> flows) {
    return flows.isEmpty()
      ? Optional.empty()
      : Optional.of(new IssueContext(adaptFlows(flows)));
  }

  private static List<Flow> adaptFlows(List<Flow> flows) {
    return flows.stream().anyMatch(Flow::hasMoreThanOneLocation)
      ? reverse(flows)
      : singletonList(groupToSingleFlow(flows));
  }

  private static Flow groupToSingleFlow(List<Flow> flows) {
    return new Flow(flows.stream()
      .flatMap(f -> f.getLocations().stream())
      .sorted(Comparator.comparing(i -> i.getRange().getStartOffset()))
      .collect(Collectors.toList()));
  }

  private static List<Flow> reverse(List<Flow> flows) {
    return flows.stream().map(f -> {
      ArrayList<Location> reorderedLocations = new ArrayList<>(f.getLocations());
      Collections.reverse(reorderedLocations);
      return new Flow(reorderedLocations);
    }).collect(Collectors.toList());
  }

  private MatchedFlowsAdapter() {
    // util class
  }
}
