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
package org.sonarlint.intellij.finding.issue;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.sonarlint.intellij.finding.tracking.Trackable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;

public class ServerIssueTrackable implements Trackable {

  private final ServerIssue<?> serverIssue;

  public ServerIssueTrackable(ServerIssue<?> serverIssue) {
    this.serverIssue = serverIssue;
  }

  @CheckForNull
  @Override
  public Integer getLine() {
    if (serverIssue instanceof LineLevelServerIssue) {
      return ((LineLevelServerIssue) serverIssue).getLine();
    }
    if (serverIssue instanceof RangeLevelServerIssue) {
      return ((RangeLevelServerIssue) serverIssue).getTextRange().getStartLine();
    }
    return null;
  }

  @Override
  public String getMessage() {
    return serverIssue.getMessage();
  }

  @CheckForNull
  @Override
  public Integer getTextRangeHash() {
    if (serverIssue instanceof RangeLevelServerIssue) {
      return ((RangeLevelServerIssue) serverIssue).getTextRange().getHash().hashCode();
    }
    return null;
  }

  @CheckForNull
  @Override
  public Integer getLineHash() {
    if (serverIssue instanceof LineLevelServerIssue) {
      return ((LineLevelServerIssue) serverIssue).getLineHash().hashCode();
    }
    return null;
  }

  @Override
  public String getRuleKey() {
    return serverIssue.getRuleKey();
  }

  @CheckForNull
  @Override
  public String getServerFindingKey() {
    return serverIssue.getKey();
  }

  @CheckForNull
  @Override
  public Long getCreationDate() {
    return serverIssue.getCreationDate().toEpochMilli();
  }

  @Override
  public boolean isResolved() {
    return serverIssue.isResolved();
  }

  @Override public IssueSeverity getUserSeverity() {
    return serverIssue.getUserSeverity();
  }

  @Nullable @Override public RuleType getType() {
    return serverIssue.getType();
  }
}
