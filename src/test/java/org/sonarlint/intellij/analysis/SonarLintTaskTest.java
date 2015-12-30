/**
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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.Before;
import org.junit.Test;
import org.sonar.runner.api.Issue;
import org.sonar.runner.api.IssueListener;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.issue.IssueProcessor;
import org.sonarlint.intellij.messages.TaskListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class SonarLintTaskTest extends LightPlatformCodeInsightFixtureTestCase {
  private SonarLintTask task;
  private IssueProcessor processor;
  private HashSet<VirtualFile> files;
  private ProgressIndicator progress;
  private SonarQubeRunnerFacade sonarQubeRunnerFacade;
  private SonarLintAnalyzer.SonarLintJob job;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    files = new HashSet<>();
    files.add(mock(VirtualFile.class));
    job = createJob();
    progress = mock(ProgressIndicator.class);
    processor = mock(IssueProcessor.class);
    task = SonarLintTask.createBackground(processor, job);
    sonarQubeRunnerFacade = SonarLintTestUtils.mockRunner(getProject());
  }

  @Test
  public void testTask() {
    TaskListener listener = mock(TaskListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(TaskListener.SONARLINT_TASK_TOPIC, listener);

    assertThat(task.shouldStartInBackground()).isTrue();
    task.run(progress);

    verify(sonarQubeRunnerFacade).startAnalysis(any(Properties.class), any(IssueListener.class));
    verify(processor).process(job, new ArrayList<Issue>());
    verify(listener).ended(job);

    verifyNoMoreInteractions(sonarQubeRunnerFacade);
    verifyNoMoreInteractions(processor);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void testCallListenerOnError() {
    TaskListener listener = mock(TaskListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(TaskListener.SONARLINT_TASK_TOPIC, listener);

    doThrow(new IllegalStateException("error")).when(sonarQubeRunnerFacade).startAnalysis(any(Properties.class), any(IssueListener.class));
    try {
      task.run(progress);
      fail("should throw exception");
    } catch(IllegalStateException e) {
      //expected
    }

    // never called because of error
    verifyZeroInteractions(processor);

    // still called
    verify(listener).ended(job);
    verifyNoMoreInteractions(listener);
  }

  private SonarLintAnalyzer.SonarLintJob createJob() {
    return new SonarLintAnalyzer.SonarLintJob(myModule, files);
  }
}
