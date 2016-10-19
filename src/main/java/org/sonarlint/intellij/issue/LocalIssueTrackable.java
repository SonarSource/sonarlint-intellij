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
package org.sonarlint.intellij.issue;

import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarlint.intellij.proto.Sonarlint;

public class LocalIssueTrackable implements Trackable {
  private Sonarlint.Issues.Issue issue;

  public LocalIssueTrackable(Sonarlint.Issues.Issue issue) {
    this.issue = issue;
  }

  @Override public Integer getLine() {
    return issue.getLine();
  }

  @Override public String getMessage() {
    return issue.getMessage();
  }

  @Override public Integer getTextRangeHash() {
    return null;
  }

  @Override public Integer getLineHash() {
    return issue.getChecksum();
  }

  @Override public String getRuleKey() {
    return issue.getRuleKey();
  }

  @Override public String getServerIssueKey() {
    return issue.getServerIssueKey();
  }

  @Override public Long getCreationDate() {
    return issue.getCreationDate();
  }

  @Override public boolean isResolved() {
    return issue.getResolved();
  }

  @Override public String getAssignee() {
    return issue.getAssignee();
  }
}
