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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarAnalyzeChangedFilesActionTest extends AbstractSonarLintLightTests {
  private SonarLintSubmitter submitter = mock(SonarLintSubmitter.class);
  private ChangeListManager changeListManager = mock(ChangeListManager.class);
  private AnActionEvent event = mock(AnActionEvent.class);

  private SonarAnalyzeChangedFilesAction action;
  private AnalysisStatus status;

  @Before
  public void before() {
    action = new SonarAnalyzeChangedFilesAction();
    status = AnalysisStatus.get(getProject());
    replaceProjectService(SonarLintSubmitter.class, submitter);
    replaceProjectService(ChangeListManager.class, changeListManager);
  }

  @Test
  public void testEnabled() {
    status.tryRun();
    assertThat(action.isEnabled(event, getProject(), status)).isFalse();

    status.stopRun();
    when(changeListManager.getAffectedFiles()).thenReturn(Collections.emptyList());
    assertThat(action.isEnabled(event, getProject(), status)).isFalse();

    status.stopRun();
    when(changeListManager.getAffectedFiles()).thenReturn(List.of(mock(VirtualFile.class)));
    assertThat(action.isEnabled(event, getProject(), status)).isTrue();
  }

  @Test
  public void testNoProject() {
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(null);
    action.actionPerformed(event);
  }

  @Test
  public void testRun() {
    AnActionEvent event = mock(AnActionEvent.class);

    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");

    when(event.getProject()).thenReturn(getProject());
    when(changeListManager.getAffectedFiles()).thenReturn(List.of(file));

    action.actionPerformed(event);

    verify(submitter).submitFiles(eq(List.of(file)), eq(TriggerType.CHANGED_FILES), any(AnalysisCallback.class), eq(false));
  }
}
