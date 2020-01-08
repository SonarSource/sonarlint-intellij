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

import java.time.Instant;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerIssueTrackableTest {
  @Test
  public void testNulls() {
    ServerIssueTrackable trackable = new ServerIssueTrackable(new NullTestIssue());

    assertThat(trackable.getServerIssueKey()).isNull();
    assertThat(trackable.getLine()).isNull();
  }

  @Test
  public void testWrapping() {
    ServerIssueTrackable trackable = new ServerIssueTrackable(new TestIssue());

    assertThat(trackable.getAssignee()).isEqualTo("assigneeLogin");
    assertThat(trackable.isResolved()).isTrue();
    assertThat(trackable.getRuleKey()).isEqualTo("ruleKey");
    assertThat(trackable.getMessage()).isEqualTo("message");
    assertThat(trackable.getLineHash()).isEqualTo("checksum".hashCode());
    assertThat(trackable.getCreationDate()).isEqualTo(1_000_000);
    assertThat(trackable.getServerIssueKey()).isEqualTo("key");
    assertThat(trackable.getLine()).isEqualTo(100);
    assertThat(trackable.getSeverity()).isEqualTo("severity");
    assertThat(trackable.getType()).isEqualTo("type");
  }

  private class NullTestIssue extends TestIssue {
    @Override public String key() {
      return "";
    }

    @Override public int line() {
      return 0;
    }
  }

  private class TestIssue implements ServerIssue {
    @Override public String key() {
      return "key";
    }

    @Override public String resolution() {
      return "resolution";
    }

    @Override public String ruleKey() {
      return "ruleKey";
    }

    @Override public int line() {
      return 100;
    }

    @Override public String message() {
      return "message";
    }

    @Override public String checksum() {
      return "checksum";
    }

    @Override public String assigneeLogin() {
      return "assigneeLogin";
    }

    @Override public String moduleKey() {
      return "moduleKey";
    }

    @Override public String filePath() {
      return "filePath";
    }

    @Override public String severity() {
      return "severity";
    }

    @Override public String type() {
      return "type";
    }

    @Override public boolean manualSeverity() {
      return false;
    }

    @Override public Instant creationDate() {
      return Instant.ofEpochMilli(1_000_000);
    }
  }
}
