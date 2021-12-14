/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.IssueMatcher;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.issue.LiveIssueBuilder;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class AnalysisTaskTest extends AbstractSonarLintLightTests {
  private AnalysisTask task;
  @Mock
  private final LiveIssueBuilder liveIssueBuilder = mock(LiveIssueBuilder.class);
  private Set<VirtualFile> filesToAnalyze = new HashSet<>();
  @Mock
  private ProgressIndicator progress;
  private AnalysisRequest analysisRequest;
  @Mock
  private SonarLintAnalyzer sonarLintAnalyzer;
  @Mock
  private AnalysisResults analysisResults;
  private final SonarLintConsole sonarLintConsole = mock(SonarLintConsole.class);
  private final IssueManager issueManagerMock = mock(IssueManager.class);

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    var testFile = myFixture.configureByText("MyClass.java", "public class MyClass {]");
    filesToAnalyze.add(testFile.getVirtualFile());
    analysisRequest = createAnalysisRequest();
    when(progress.isCanceled()).thenReturn(false);
    when(analysisResults.failedAnalysisFiles()).thenReturn(Collections.emptyList());
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class))).thenReturn(analysisResults);

    replaceProjectService(AnalysisStatus.class, new AnalysisStatus(getProject()));
    replaceProjectService(SonarLintAnalyzer.class, sonarLintAnalyzer);
    replaceProjectService(SonarLintConsole.class, sonarLintConsole);
    replaceProjectService(ServerIssueUpdater.class, mock(ServerIssueUpdater.class));
    replaceProjectService(IssueManager.class, issueManagerMock);
    replaceProjectService(LiveIssueBuilder.class, liveIssueBuilder);

    task = new AnalysisTask(analysisRequest, false, true);

    // IntelliJ light test fixtures appear to reuse the same project container, so we need to ensure that status is stopped.
    AnalysisStatus.get(getProject()).stopRun();
  }

  @Test
  public void testTask() {
    assertThat(task.shouldStartInBackground()).isTrue();
    assertThat(task.isConditionalModal()).isFalse();
    assertThat(task.getRequest()).isEqualTo(analysisRequest);
    task.run(progress);

    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class));

    assertThat(getExternalAnnotators())
      .extracting("implementationClass")
      .contains("org.sonarlint.intellij.editor.SonarExternalAnnotator");
    verifyNoMoreInteractions(sonarLintAnalyzer);
    verifyNoMoreInteractions(liveIssueBuilder);
  }

  @Test
  public void shouldIgnoreProjectLevelIssues() {
    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        IssueListener issueListener = invocation.getArgument(2);
        Issue issue = SonarLintTestUtils.createIssue(1);
        issueListener.handle(issue);
        return analysisResults;
      });

    task.run(progress);

    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class));
    verify(issueManagerMock, never()).insertNewIssue(any(), any());
    verifyNoMoreInteractions(liveIssueBuilder);
  }

  @Test
  public void shouldIgnoreInvalidFiles() {
    var vFile = filesToAnalyze.iterator().next();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      try {
        vFile.delete(null);
      } catch (IOException e) {
        fail("Cannot delete file");
      }
    });
    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);

    task.run(progress);

    verifyZeroInteractions(sonarLintAnalyzer);
    verify(issueManagerMock, never()).insertNewIssue(any(), any());
    verifyZeroInteractions(liveIssueBuilder);
  }

  @Test
  public void shouldReportIssueForValidFile() throws Exception {
    var issue = mock(Issue.class);
    var clientInputFile = mock(ClientInputFile.class);
    when(issue.getInputFile()).thenReturn(clientInputFile);
    var virtualFile = filesToAnalyze.iterator().next();
    var liveIssue = mock(LiveIssue.class);
    when(clientInputFile.getClientObject()).thenReturn(virtualFile);
    when(clientInputFile.getPath()).thenReturn("path");
    when(liveIssueBuilder.buildLiveIssue(any(), any())).thenReturn(liveIssue);

    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        IssueListener issueListener = invocation.getArgument(2);
        issueListener.handle(issue);
        return analysisResults;
      });

    task.run(progress);

    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class));
    verify(liveIssueBuilder).buildLiveIssue(any(), any());
    verify(issueManagerMock).insertNewIssue(virtualFile, liveIssue);
  }

  @Test
  public void shouldSkipIfFailedToFindIssueLocation() throws Exception {
    var issue = buildValidIssue();
    when(liveIssueBuilder.buildLiveIssue(any(), any())).thenThrow(new IssueMatcher.NoMatchException(""));

    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        IssueListener issueListener = invocation.getArgument(2);
        issueListener.handle(issue);
        return analysisResults;
      });

    task.run(progress);

    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class));
    verify(issueManagerMock, never()).insertNewIssue(any(), any());
    verify(liveIssueBuilder).buildLiveIssue(any(), any());
    verifyNoMoreInteractions(liveIssueBuilder);
  }

  @Test
  public void shouldSkipIfException() throws Exception {
    var issue = buildValidIssue();
    when(liveIssueBuilder.buildLiveIssue(any(), any())).thenThrow(new RuntimeException(""));

    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        IssueListener issueListener = invocation.getArgument(2);
        issueListener.handle(issue);
        return analysisResults;
      });

    task.run(progress);

    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class));
    verify(issueManagerMock, never()).insertNewIssue(any(), any());
    verify(liveIssueBuilder).buildLiveIssue(any(), any());
    verifyNoMoreInteractions(liveIssueBuilder);
  }

  @Test
  public void testAnalysisError() {
    doThrow(new IllegalStateException("error")).when(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(IssueListener.class), any(ClientProgressMonitor.class));
    task.run(progress);

    // never called because of error
    verifyZeroInteractions(liveIssueBuilder);
  }

  @Test
  public void testCancel() {
    task.cancel();
    task.run(progress);

    verify(sonarLintConsole).info("Analysis canceled");

    // never called because of cancel
    verifyZeroInteractions(liveIssueBuilder);
  }

  @Test
  public void testFinishFlag() {
    assertThat(task.isFinished()).isFalse();
    task.onFinished();
    assertThat(task.isFinished()).isTrue();
  }

  private AnalysisRequest createAnalysisRequest() {
    return new AnalysisRequest(getProject(), filesToAnalyze, TriggerType.ACTION, false, mock(AnalysisCallback.class));
  }

  private List<LanguageExtensionPoint<?>> getExternalAnnotators() {
    ExtensionPoint<LanguageExtensionPoint<?>> extensionPoint = Extensions.getRootArea().getExtensionPoint("com.intellij.externalAnnotator");
    return extensionPoint.extensions().collect(Collectors.toList());
  }

  @NotNull
  private Issue buildValidIssue() {
    var issue = mock(Issue.class);
    var clientInputFile = mock(ClientInputFile.class);
    when(issue.getInputFile()).thenReturn(clientInputFile);
    var virtualFile = filesToAnalyze.iterator().next();
    when(clientInputFile.getClientObject()).thenReturn(virtualFile);
    when(clientInputFile.getPath()).thenReturn("path");
    return issue;
  }
}
