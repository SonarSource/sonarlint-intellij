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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.vfs.VirtualFile;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintMockedTests;
import org.sonarlint.intellij.messages.AnalysisResultsListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class IssueStoreTest extends AbstractSonarLintMockedTests {
  private IssueStore issueStore;
  private final AnalysisResultsListener listener = mock(AnalysisResultsListener.class);

  @Before
  public void prepare() {
    project.getMessageBus().connect().subscribe(AnalysisResultsListener.ANALYSIS_RESULTS_TOPIC, listener);
    issueStore = new IssueStore(project);
  }

  @Test
  public void testSet() {
    Map<VirtualFile, Collection<LiveIssue>> issues = Map.of(
      mock(VirtualFile.class), List.of(mock(LiveIssue.class)),
      mock(VirtualFile.class), List.of(mock(LiveIssue.class))
    );

    issueStore.set(issues, "3 files");
    assertThat(issueStore.lastAnalysisDate())
      .isBetween(
        Instant.now().minus(Duration.ofSeconds(3)),
        Instant.now()
      );
    assertThat(issueStore.issues()).isEqualTo(issues);

    verify(listener).update(issues);

    // everything should be done even if it's an empty map
    issueStore.set(Collections.emptyMap(), "3 files");
    assertThat(issueStore.lastAnalysisDate())
      .isBetween(
        Instant.now().minus(Duration.ofSeconds(3)),
        Instant.now()
      );
    assertThat(issueStore.wasAnalyzed()).isTrue();
    assertThat(issueStore.issues()).isEmpty();
    assertThat(issueStore.getTopic()).isEqualTo(AnalysisResultsListener.ANALYSIS_RESULTS_TOPIC);

    verify(listener).update(Collections.emptyMap());
  }

  @Test
  public void testClear() {
    Map<VirtualFile, Collection<LiveIssue>> issues = Map.of(
      mock(VirtualFile.class), List.of(mock(LiveIssue.class)),
      mock(VirtualFile.class), List.of(mock(LiveIssue.class))
    );

    issueStore.set(issues, "3 files");
    issueStore.clear();

    verify(listener).update(Collections.emptyMap());
    assertThat(issueStore.lastAnalysisDate()).isNull();
    assertThat(issueStore.issues()).isEmpty();
    assertThat(issueStore.wasAnalyzed()).isFalse();

  }
}
