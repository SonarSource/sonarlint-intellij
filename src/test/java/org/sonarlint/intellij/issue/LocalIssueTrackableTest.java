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

import org.junit.Test;
import org.sonarlint.intellij.proto.Sonarlint;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalIssueTrackableTest {
  @Test
  public void testWrapping() {
    Sonarlint.Issues.Issue issue = Sonarlint.Issues.Issue.newBuilder()
      .setServerIssueKey("key")
      .setAssignee("assignee")
      .setLine(10)
      .setChecksum(20)
      .setMessage("msg")
      .setResolved(true)
      .setCreationDate(1000L)
      .setRuleKey("ruleKey")
      .build();

    LocalIssueTrackable trackable = new LocalIssueTrackable(issue);
    assertThat(trackable.getAssignee()).isEqualTo("assignee");
    assertThat(trackable.getMessage()).isEqualTo("msg");
    assertThat(trackable.getServerIssueKey()).isEqualTo("key");
    assertThat(trackable.getLine()).isEqualTo(10);
    assertThat(trackable.getCreationDate()).isEqualTo(1000L);
    assertThat(trackable.isResolved()).isTrue();
    assertThat(trackable.getLineHash()).isEqualTo(20);
    assertThat(trackable.getTextRangeHash()).isNull();
    assertThat(trackable.getRuleKey()).isEqualTo("ruleKey");
  }

  @Test
  public void testNulls() {
    Sonarlint.Issues.Issue issue = Sonarlint.Issues.Issue.newBuilder()
      .build();

    LocalIssueTrackable trackable = new LocalIssueTrackable(issue);
    assertThat(trackable.getServerIssueKey()).isNull();
    assertThat(trackable.getLine()).isNull();
    assertThat(trackable.getCreationDate()).isNull();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void severityNotStored() {
    Sonarlint.Issues.Issue issue = Sonarlint.Issues.Issue.newBuilder()
      .build();

    LocalIssueTrackable trackable = new LocalIssueTrackable(issue);
    trackable.getSeverity();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void typeNotStored() {
    Sonarlint.Issues.Issue issue = Sonarlint.Issues.Issue.newBuilder()
      .build();

    LocalIssueTrackable trackable = new LocalIssueTrackable(issue);
    trackable.getType();
  }
}
