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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

public class ServerIssueTrackable implements Trackable {

  private final ServerIssue serverIssue;

  public ServerIssueTrackable(ServerIssue serverIssue) {
    this.serverIssue = serverIssue;
  }

  @CheckForNull
  @Override
  public Integer getLine() {
    return serverIssue.line() != 0 ? serverIssue.line() : null;
  }

  @Override
  public String getMessage() {
    return serverIssue.message();
  }

  @CheckForNull
  @Override
  public Integer getTextRangeHash() {
    return null;
  }

  @CheckForNull
  @Override
  public Integer getLineHash() {
    return serverIssue.checksum().hashCode();
  }

  @Override
  public String getRuleKey() {
    return serverIssue.ruleKey();
  }

  @CheckForNull
  @Override
  public String getServerIssueKey() {
    return !serverIssue.key().isEmpty() ? serverIssue.key() : null;
  }

  @CheckForNull
  @Override
  public Long getCreationDate() {
    return serverIssue.creationDate().toEpochMilli();
  }

  @Override
  public boolean isResolved() {
    return !serverIssue.resolution().isEmpty();
  }

  @Override public String getAssignee() {
    return serverIssue.assigneeLogin();
  }

  @Override public String getSeverity() {
    return serverIssue.severity();
  }

  @Nullable @Override public String getType() {
    return serverIssue.type();
  }
}
