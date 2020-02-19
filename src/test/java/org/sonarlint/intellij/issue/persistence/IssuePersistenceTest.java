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
package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarlint.intellij.issue.LocalIssueTrackable;
import org.sonarlint.intellij.issue.tracking.Trackable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssuePersistenceTest {
  private Project project = mock(Project.class);
  private IssuePersistence persistence;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setUp() {
    VirtualFile baseDir = mock(VirtualFile.class);

    when(project.getBaseDir()).thenReturn(baseDir);
    when(baseDir.getPath()).thenReturn(temp.getRoot().getAbsolutePath());
    when(baseDir.findFileByRelativePath(anyString())).thenReturn(baseDir);
    persistence = new IssuePersistence(project);
  }

  @Test
  public void should_store_read_empty() throws IOException {
    persistence.save("key", Collections.emptyList());
    assertThat(persistence.read("key")).isEmpty();
  }

  @Test
  public void should_clear() throws IOException {
    persistence.save("key", Collections.singleton(testTrackable));
    persistence.clear();
    assertThat(persistence.read("key")).isNull();
  }

  @Test
  public void should_clear_specific_key() throws IOException {
    persistence.save("key1", Collections.singleton(testTrackable));
    persistence.save("key2", Collections.singleton(testTrackable));
    persistence.clear("key2");
    assertThat(persistence.read("key1")).isNotNull();
    assertThat(persistence.read("key2")).isNull();
  }

  @Test
  public void should_not_read_if_not_stored() throws IOException {
    assertThat(persistence.read("key")).isNull();
  }

  @Test
  public void should_store_read() throws IOException {
    persistence.save("key", Collections.singleton(testTrackable));

    Collection<LocalIssueTrackable> issues = persistence.read("key");
    assertThat(issues).hasSize(1);

    LocalIssueTrackable issue = issues.iterator().next();
    assertThat(issue.getAssignee()).isEqualTo("assignee");
    assertThat(issue.getRuleKey()).isEqualTo("ruleKey");
    assertThat(issue.getMessage()).isEqualTo("msg");
    assertThat(issue.getLine()).isEqualTo(5);
    assertThat(issue.getTextRangeHash()).isNull();
    assertThat(issue.getLineHash()).isEqualTo(3);
    assertThat(issue.getServerIssueKey()).isEqualTo("serverKey");
  }

  private Trackable testTrackable = new Trackable() {
    @Override public Integer getLine() {
      return 5;
    }

    @Override public String getMessage() {
      return "msg";
    }

    @Override public Integer getTextRangeHash() {
      return 4;
    }

    @Override public Integer getLineHash() {
      return 3;
    }

    @Override public String getRuleKey() {
      return "ruleKey";
    }

    @Override public String getServerIssueKey() {
      return "serverKey";
    }

    @Override public Long getCreationDate() {
      return 1000L;
    }

    @Override public boolean isResolved() {
      return false;
    }

    @Override public String getAssignee() {
      return "assignee";
    }

    @Override public String getSeverity() {
      return "severity";
    }

    @Nullable @Override public String getType() {
      return "type";
    }
  };
}
