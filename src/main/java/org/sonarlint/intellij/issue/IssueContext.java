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
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.util.SonarLintUtils;

public class IssueContext {
  private final String description;
  private final List<LiveIssue.Flow> flows;
  private final List<LiveIssue.SecondaryLocation> secondaryLocations;

  public IssueContext(List<LiveIssue.Flow> flows) {
    if (flows.stream().anyMatch(f -> f.locations().size() > 1)) {
      this.flows = reverseAll(flows);
      this.secondaryLocations = Collections.emptyList();
    } else {
      this.flows = Collections.emptyList();
      this.secondaryLocations = flows.stream()
        .flatMap(f -> f.locations().stream())
        .sorted(Comparator.comparing(i -> i.location().getStartOffset()))
        .collect(Collectors.toList());
    }
    description = computeDescription();
  }

  @NotNull
  private static List<LiveIssue.Flow> reverseAll(List<LiveIssue.Flow> flows) {
    return flows.stream().map(f -> {
      ArrayList<LiveIssue.SecondaryLocation> reorderedLocations = new ArrayList<>(f.locations());
      Collections.reverse(reorderedLocations);
      return new LiveIssue.Flow(reorderedLocations);
    }).collect(Collectors.toList());
  }

  private String computeDescription() {
    String desc;
    if (flows().size() > 1) {
      desc = String.format(" [+%d flows]", flows().size());
    } else {
      int numLocations = flows().size() == 1 ? flows.get(0).locations().size() : secondaryLocations.size();
      desc = String.format(" [+%d %s]", numLocations, SonarLintUtils.pluralize("location", numLocations));
    }
    return desc;
  }

  public String getDescription() {
    return description;
  }

  public List<LiveIssue.Flow> flows() {
    return flows;
  }

  public List<LiveIssue.SecondaryLocation> secondaryLocations() {
    return secondaryLocations;
  }

  public boolean hasFlows() {
    return !flows.isEmpty();
  }

  public boolean hasUniqueFlow() {
    return flows().size() == 1;
  }
}
