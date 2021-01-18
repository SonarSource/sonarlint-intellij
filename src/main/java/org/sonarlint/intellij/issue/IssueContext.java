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

import java.util.List;
import org.sonarlint.intellij.util.SonarLintUtils;

public class IssueContext {
  private final String summaryDescription;
  private final List<Flow> flows;

  public IssueContext(List<Flow> flows) {
    this.flows = flows;
    summaryDescription = computeSummaryDescription();
  }

  private String computeSummaryDescription() {
    String description;
    if (hasUniqueFlow()) {
      int numLocations = flows.get(0).getLocations().size();
      description = String.format(" [+%d %s]", numLocations, SonarLintUtils.pluralize("location", numLocations));
    } else {
      description = String.format(" [+%d flows]", flows().size());
    }
    return description;
  }

  public String getSummaryDescription() {
    return summaryDescription;
  }

  public List<Flow> flows() {
    return flows;
  }

  public boolean hasUniqueFlow() {
    return flows().size() == 1;
  }
}
