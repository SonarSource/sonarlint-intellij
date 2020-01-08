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

import com.intellij.openapi.vfs.VirtualFile;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.messages.AnalysisResultsListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AnalysisResultIssuesTest extends SonarTest {
  private AnalysisResultIssues analysisResultIssues;
  private AnalysisResultsListener listener = mock(AnalysisResultsListener.class);

  @Before
  public void prepare() {
    project.getMessageBus().connect().subscribe(AnalysisResultsListener.ANALYSIS_RESULTS_TOPIC, listener);
    analysisResultIssues = new AnalysisResultIssues(project);
  }

  @Test
  public void testSet() {
    Map<VirtualFile, Collection<LiveIssue>> issues = new HashMap<>();
    issues.put(mock(VirtualFile.class), Collections.singletonList(mock(LiveIssue.class)));
    issues.put(mock(VirtualFile.class), Collections.singletonList(mock(LiveIssue.class)));

    analysisResultIssues.set(issues, "3 files");
    assertThat(analysisResultIssues.lastAnalysisDate())
      .isLessThanOrEqualTo(Instant.now())
      .isGreaterThan(Instant.now().minus(Duration.ofSeconds(3)));
    assertThat(analysisResultIssues.issues()).isEqualTo(issues);

    verify(listener).update(issues);

    // everything should be done even if it's an empty map
    analysisResultIssues.set(Collections.emptyMap(), "3 files");
    assertThat(analysisResultIssues.lastAnalysisDate())
      .isLessThanOrEqualTo(Instant.now())
      .isGreaterThan(Instant.now().minus(Duration.ofSeconds(3)));
    assertThat(analysisResultIssues.wasAnalyzed()).isTrue();
    assertThat(analysisResultIssues.issues()).isEmpty();
    assertThat(analysisResultIssues.getTopic()).isEqualTo(AnalysisResultsListener.ANALYSIS_RESULTS_TOPIC);

    verify(listener).update(Collections.emptyMap());
  }

  @Test
  public void testClear() {
    Map<VirtualFile, Collection<LiveIssue>> issues = new HashMap<>();
    issues.put(mock(VirtualFile.class), Collections.singletonList(mock(LiveIssue.class)));
    issues.put(mock(VirtualFile.class), Collections.singletonList(mock(LiveIssue.class)));

    analysisResultIssues.set(issues, "3 files");
    analysisResultIssues.clear();

    verify(listener).update(Collections.emptyMap());
    assertThat(analysisResultIssues.lastAnalysisDate()).isNull();
    assertThat(analysisResultIssues.issues()).isEmpty();
    assertThat(analysisResultIssues.wasAnalyzed()).isFalse();

  }
}
