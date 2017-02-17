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

import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChangedFilesScopeTest extends SonarTest {
  private ChangedFilesScope scope;
  private ChangedFilesIssues issues;
  private ChangeListManager changeListManager;
  private ProjectLevelVcsManager vcsManager;

  @Before
  public void prepare() {
    super.register(app, SonarLintActions.class, mock(SonarLintActions.class, RETURNS_DEEP_STUBS));
    vcsManager = mock(ProjectLevelVcsManager.class);
    changeListManager = mock(ChangeListManager.class);
    issues = mock(ChangedFilesIssues.class);
    super.register(ProjectLevelVcsManager.class, vcsManager);
    super.register(ChangeListManager.class, changeListManager);
    super.register(ChangedFilesIssues.class, issues);
    scope = new ChangedFilesScope(project);
  }

  @Test
  public void testNoVCS() {
    assertThat(scope.getDisplayName()).isEqualTo("VCS changed files");
    assertThat(scope.getLastAnalysisDate()).isNull();
    assertThat(scope.getLabelText()).isEqualTo("Project has no active VCS");
    assertThat(scope.getEmptyText()).isEqualTo("No changed files in the VCS");
    assertThat(scope.toolbarActionGroup()).isNotNull();
    assertThat(scope.issues()).isEmpty();
  }

  @Test
  public void testNoChangedFiles() {
    when(vcsManager.hasActiveVcss()).thenReturn(true);
    assertThat(scope.getLabelText()).isEqualTo("VCS contains no changed files");
    assertThat(scope.getEmptyText()).isEqualTo("No changed files in the VCS");
  }

  @Test
  public void testReadyToRun() {
    when(vcsManager.hasActiveVcss()).thenReturn(true);
    VirtualFile file = mock(VirtualFile.class);
    when(changeListManager.getAffectedFiles()).thenReturn(Collections.singletonList(file));
    assertThat(scope.getLabelText()).isEqualTo("Trigger the analysis to find issues on the files in the VCS change set");
    assertThat(scope.getEmptyText()).isEqualTo("No analysis done on changed files");
  }

  @Test
  public void testContainsIssues() {
    VirtualFile file = mock(VirtualFile.class);
    LiveIssue issue = mock(LiveIssue.class);
    when(vcsManager.hasActiveVcss()).thenReturn(true);
    when(changeListManager.getAffectedFiles()).thenReturn(Collections.singletonList(file));
    when(issues.lastAnalysisDate()).thenReturn(Instant.now());
    when(issues.wasAnalyzed()).thenReturn(true);
    when(issues.issues()).thenReturn(Collections.singletonMap(file, Collections.singleton(issue)));

    assertThat(scope.getLastAnalysisDate()).isNotNull();
    assertThat(scope.getEmptyText()).isEqualTo("No issues in changed files");
    assertThat(scope.issues()).hasSize(1);
  }
}
