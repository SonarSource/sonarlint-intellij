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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.IssueProcessor;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarLintTaskTest extends SonarTest {
  private SonarLintTask task;
  @Mock
  private IssueProcessor processor;
  private HashSet<VirtualFile> files;
  @Mock
  private ProgressIndicator progress;
  private SonarLintJob job;
  @Mock
  private SonarLintAnalyzer sonarLintAnalyzer;
  @Mock
  private AnalysisResults analysisResults;
  @Mock
  private SonarApplication sonarApplication;

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    files = new HashSet<>();
    VirtualFile testFile = mock(VirtualFile.class);
    files.add(testFile);
    job = createJob();
    when(progress.isCanceled()).thenReturn(false);
    when(analysisResults.failedAnalysisFiles()).thenReturn(Collections.emptyList());
    when(sonarLintAnalyzer.analyzeModule(eq(module), eq(files), any(IssueListener.class), any(ProgressMonitor.class))).thenReturn(analysisResults);

    super.register(SonarLintStatus.class, new SonarLintStatus(getProject()));
    super.register(SonarLintAnalyzer.class, sonarLintAnalyzer);
    super.register(SonarLintConsole.class, mock(SonarLintConsole.class));
    super.register(ServerIssueUpdater.class, mock(ServerIssueUpdater.class));
    super.register(IssueManager.class, mock(IssueManager.class));

    task = new SonarLintTask(processor, job, false, true, sonarApplication);

    //IntelliJ light test fixtures appear to reuse the same project container, so we need to ensure that status is stopped.
    SonarLintStatus.get(getProject()).stopRun();
  }

  @Test
  public void testTask() {
    TaskListener listener = mock(TaskListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(TaskListener.SONARLINT_TASK_TOPIC, listener);

    assertThat(task.shouldStartInBackground()).isTrue();
    assertThat(task.isConditionalModal()).isFalse();
    assertThat(task.getJob()).isEqualTo(job);
    task.run(progress);

    verify(sonarApplication).registerExternalAnnotator();
    verify(sonarLintAnalyzer).analyzeModule(eq(module), eq(files), any(IssueListener.class), any(ProgressMonitor.class));
    verify(processor).process(job, progress, new ArrayList<>(), new ArrayList<>());
    verify(listener).ended(job);

    verifyNoMoreInteractions(sonarApplication);
    verifyNoMoreInteractions(sonarLintAnalyzer);
    verifyNoMoreInteractions(processor);
  }

  @Test
  public void testCallListenerOnError() {
    TaskListener listener = mock(TaskListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(TaskListener.SONARLINT_TASK_TOPIC, listener);

    doThrow(new IllegalStateException("error")).when(sonarLintAnalyzer).analyzeModule(eq(module), eq(files), any(IssueListener.class), any(ProgressMonitor.class));
    task.run(progress);

    // never called because of error
    verifyZeroInteractions(processor);

    // still called
    verify(sonarApplication).registerExternalAnnotator();
    verify(listener).ended(job);
    verifyNoMoreInteractions(listener);
  }

  private SonarLintJob createJob() {
    return new SonarLintJob(module, files, Collections.emptyList(), TriggerType.ACTION);
  }
}
