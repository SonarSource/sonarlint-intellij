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

import com.intellij.openapi.vfs.VirtualFile;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.messages.ChangedFilesIssuesListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ChangedFilesIssuesTest extends SonarTest {
  private ChangedFilesIssues changedFilesIssues;
  private ChangedFilesIssuesListener listener;

  @Before
  public void setUp() {
    super.setUp();
    listener = mock(ChangedFilesIssuesListener.class);
    project.getMessageBus().connect().subscribe(ChangedFilesIssuesListener.CHANGED_FILES_ISSUES_TOPIC, listener);
    changedFilesIssues = new ChangedFilesIssues(project);
  }

  @Test
  public void testSet() {
    Map<VirtualFile, Collection<LiveIssue>> issues = new HashMap<>();
    issues.put(mock(VirtualFile.class), Collections.singletonList(mock(LiveIssue.class)));
    issues.put(mock(VirtualFile.class), Collections.singletonList(mock(LiveIssue.class)));

    changedFilesIssues.set(issues);
    assertThat(changedFilesIssues.lastAnalysisDate())
      .isLessThanOrEqualTo(LocalDateTime.now())
      .isGreaterThan(LocalDateTime.now().minus(Duration.ofSeconds(3)));
    assertThat(changedFilesIssues.issues()).isEqualTo(issues);

    verify(listener).update(issues);

    // everything should be done even if it's an empty map
    changedFilesIssues.set(Collections.emptyMap());
    assertThat(changedFilesIssues.lastAnalysisDate())
      .isLessThanOrEqualTo(LocalDateTime.now())
      .isGreaterThan(LocalDateTime.now().minus(Duration.ofSeconds(3)));
    assertThat(changedFilesIssues.issues()).isEmpty();

    verify(listener).update(Collections.emptyMap());
  }

  @Test
  public void testClear() {
    Map<VirtualFile, Collection<LiveIssue>> issues = new HashMap<>();
    issues.put(mock(VirtualFile.class), Collections.singletonList(mock(LiveIssue.class)));
    issues.put(mock(VirtualFile.class), Collections.singletonList(mock(LiveIssue.class)));

    changedFilesIssues.set(issues);
    changedFilesIssues.clear();

    verify(listener).update(Collections.emptyMap());
    assertThat(changedFilesIssues.lastAnalysisDate()).isNull();
    assertThat(changedFilesIssues.issues()).isEmpty();
  }
}
