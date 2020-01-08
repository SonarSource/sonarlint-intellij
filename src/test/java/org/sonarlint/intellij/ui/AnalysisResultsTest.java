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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.issue.AnalysisResultIssues;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AnalysisResultsTest extends SonarTest {
  private AnalysisResultIssues issues = register(AnalysisResultIssues.class);
  private AnalysisResults analysisResults = new AnalysisResults(project);

  @Before
  public void prepare() {
    super.register(app, SonarLintActions.class, mock(SonarLintActions.class, RETURNS_DEEP_STUBS));
  }

  @Test
  public void testNotAnalyzed() {
    assertThat(analysisResults.getLastAnalysisDate()).isNull();
    assertThat(analysisResults.getLabelText()).isEqualTo("Trigger an analysis to find issues in the project sources");
    assertThat(analysisResults.getEmptyText()).isEqualTo("No analysis done");
    assertThat(analysisResults.issues()).isEmpty();
  }

  @Test
  public void testContainsIssues() {
    VirtualFile file = mock(VirtualFile.class);
    LiveIssue issue = mock(LiveIssue.class);
    when(issues.lastAnalysisDate()).thenReturn(Instant.now());
    when(issues.wasAnalyzed()).thenReturn(true);
    when(issues.issues()).thenReturn(Collections.singletonMap(file, Collections.singleton(issue)));

    assertThat(analysisResults.getLastAnalysisDate()).isNotNull();
    assertThat(analysisResults.getEmptyText()).isEqualTo("No issues found");
    assertThat(analysisResults.issues()).hasSize(1);
  }
}
