/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import java.time.Instant;
import org.junit.Test;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerIssueTrackableTest {
  @Test
  public void test_file_level_issue() {
    var trackable = new ServerIssueTrackable(new FileLevelServerIssue("issueUuid", true, "ruleKey", "msg", "filePath", Instant.now(), null, RuleType.BUG));

    assertThat(trackable.getLine()).isNull();
    assertThat(trackable.getTextRangeHash()).isNull();
    assertThat(trackable.getLineHash()).isNull();
  }

  @Test
  public void test_line_level_issue() {
    var trackable = new ServerIssueTrackable(
      new LineLevelServerIssue("key", true, "ruleKey", "message", "lineHash", "filePath", Instant.ofEpochMilli(1_000_000), IssueSeverity.MINOR, RuleType.VULNERABILITY, 100));

    assertThat(trackable.isResolved()).isTrue();
    assertThat(trackable.getRuleKey()).isEqualTo("ruleKey");
    assertThat(trackable.getMessage()).isEqualTo("message");
    assertThat(trackable.getLineHash()).isEqualTo("lineHash".hashCode());
    assertThat(trackable.getTextRangeHash()).isNull();
    assertThat(trackable.getCreationDate()).isEqualTo(1_000_000);
    assertThat(trackable.getServerIssueKey()).isEqualTo("key");
    assertThat(trackable.getLine()).isEqualTo(100);
    assertThat(trackable.getUserSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(trackable.getType()).isEqualTo(RuleType.VULNERABILITY);
  }

  @Test
  public void test_range_level_issue() {
    var trackable = new ServerIssueTrackable(new RangeLevelServerIssue("key", true, "ruleKey", "message", "filePath", Instant.ofEpochMilli(1_000_000), IssueSeverity.INFO,
      RuleType.BUG, new TextRangeWithHash(1, 2, 3, 4, "rangeHash")));

    assertThat(trackable.isResolved()).isTrue();
    assertThat(trackable.getRuleKey()).isEqualTo("ruleKey");
    assertThat(trackable.getMessage()).isEqualTo("message");
    assertThat(trackable.getLineHash()).isNull();
    assertThat(trackable.getTextRangeHash()).isEqualTo("rangeHash".hashCode());
    assertThat(trackable.getCreationDate()).isEqualTo(1_000_000);
    assertThat(trackable.getServerIssueKey()).isEqualTo("key");
    assertThat(trackable.getLine()).isEqualTo(1);
    assertThat(trackable.getUserSeverity()).isEqualTo(IssueSeverity.INFO);
    assertThat(trackable.getType()).isEqualTo(RuleType.BUG);
  }

}
