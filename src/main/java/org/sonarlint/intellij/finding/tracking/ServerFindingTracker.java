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
package org.sonarlint.intellij.finding.tracking;

import java.util.Collection;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;

public class ServerFindingTracker {
  public <T extends LiveFinding> void matchLocalWithServerFindings(final Collection<Trackable> serverFindings,
    Collection<T> previousFindings) {
    Input<Trackable> baseInput = () -> serverFindings;
    Input<T> rawInput = () -> previousFindings;

    trackServerFinding(baseInput, rawInput);
  }

  private static <T extends Trackable, L extends LiveFinding> void trackServerFinding(Input<T> baseInput, Input<L> rawInput) {
    var tracking = new Tracker<L, T>().track(rawInput, baseInput);
    for (var entry : tracking.getMatchedRaws().entrySet()) {
      var liveMatched = entry.getKey();
      var serverMatched = entry.getValue();
      copyAttributesFromServer(liveMatched, serverMatched);
    }
    for (var liveUnmatched : tracking.getUnmatchedRaws()) {
      if (liveUnmatched.getServerFindingKey() != null) {
        // were matched before with server finding, now not anymore
        wipeServerFindingDetails(liveUnmatched);
      }
    }
  }

  private static <L extends LiveFinding> void copyAttributesFromServer(L liveFinding, Trackable serverFinding) {
    liveFinding.setIntroductionDate(serverFinding.getIntroductionDate());
    liveFinding.setServerFindingKey(serverFinding.getServerFindingKey());
    liveFinding.setResolved(serverFinding.isResolved());
    IssueSeverity userSeverity = serverFinding.getUserSeverity();
    if (userSeverity != null) {
      liveFinding.setSeverity(userSeverity);
    }
    if (liveFinding instanceof LiveIssue) {
      ((LiveIssue) liveFinding).setType(serverFinding.getType());
    }
  }

  private static <L extends LiveFinding> void wipeServerFindingDetails(L finding) {
    // we keep creation date from the old server issue
    finding.setServerFindingKey(null);
    finding.setResolved(false);
  }
}
