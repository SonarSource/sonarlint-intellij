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
package org.sonarlint.intellij.actions;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarAnalyzeChangedFilesActionTest extends SonarTest {
  private SonarAnalyzeChangedFilesAction action;
  @Mock
  private SonarLintSubmitter submitter;
  @Mock
  private SonarLintStatus status;
  @Mock
  private ChangedFilesIssues issues;

  @Mock
  private PeriodicalTasksCloser tasksCloser;
  @Mock
  private ChangeListManager changeListManager;

  @Before
  public void before() {
    action = new SonarAnalyzeChangedFilesAction();
    MockitoAnnotations.initMocks(this);
    when(tasksCloser.safeGetComponent(project, ChangeListManager.class)).thenReturn(changeListManager);

    super.register(app, PeriodicalTasksCloser.class, tasksCloser);
    super.register(SonarLintSubmitter.class, submitter);
    super.register(ChangedFilesIssues.class, issues);
  }

  @Test
  public void testEnabled() {
    when(status.isRunning()).thenReturn(true);
    assertThat(action.isEnabled(project, status)).isFalse();

    when(status.isRunning()).thenReturn(false);
    assertThat(action.isEnabled(project, status)).isTrue();
  }

  @Test
  public void testRun() {
    AnActionEvent event = mock(AnActionEvent.class);

    VirtualFile file = mock(VirtualFile.class);
    CompletableFuture<AnalysisResult> result = mock(CompletableFuture.class);

    when(event.getProject()).thenReturn(project);
    when(changeListManager.getAffectedFiles()).thenReturn(Collections.singletonList(file));
    when(submitter.submitFiles(Collections.singletonList(file), TriggerType.ACTION, false, true)).thenReturn(result);

    action.actionPerformed(event);

    verify(submitter).submitFiles(Collections.singletonList(file), TriggerType.ACTION, false, true);
    verify(result).thenAccept(any(Consumer.class));
  }
}
