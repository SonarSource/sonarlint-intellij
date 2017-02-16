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
package org.sonarlint.intellij.ui.scope;

import com.intellij.openapi.vfs.VirtualFile;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.issue.AllFilesIssues;
import org.sonarlint.intellij.issue.LiveIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AllFilesScopeTest extends SonarTest {
  private AllFilesScope scope;
  private AllFilesIssues issues;

  @Before
  public void prepare() {
    issues = mock(AllFilesIssues.class);
    super.register(AllFilesIssues.class, issues);
    scope = new AllFilesScope(project);
  }

  @Test
  public void testNotAnalyzed() {
    assertThat(scope.getDisplayName()).isEqualTo("All project files");
    assertThat(scope.getLastAnalysisDate()).isNull();
    assertThat(scope.getLabelText()).isEqualTo("Trigger the analysis to find issues in all project sources");
    assertThat(scope.getEmptyText()).isEqualTo("No analysis done");
    assertThat(scope.toolbarId()).isEqualTo("SonarLint.resultstoolwindow");
    assertThat(scope.issues()).isEmpty();
  }

  @Test
  public void testContainsIssues() {
    VirtualFile file = mock(VirtualFile.class);
    LiveIssue issue = mock(LiveIssue.class);
    when(issues.lastAnalysisDate()).thenReturn(LocalDateTime.now());
    when(issues.wasAnalyzed()).thenReturn(true);
    when(issues.issues()).thenReturn(Collections.singletonMap(file, Collections.singleton(issue)));

    assertThat(scope.getLastAnalysisDate()).isNotNull();
    assertThat(scope.getEmptyText()).isEqualTo("No issues found");
    assertThat(scope.issues()).hasSize(1);
  }
}
