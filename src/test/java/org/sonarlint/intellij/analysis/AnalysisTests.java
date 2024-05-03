/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.finding.persistence.CachedFindings;
import org.sonarlint.intellij.finding.persistence.FindingsCache;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssue;
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssueListener;
import org.sonarsource.sonarlint.core.commons.api.progress.ClientProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

class AnalysisTests extends AbstractSonarLintLightTests {
  private Analysis task;
  private int analysisTaskTimeout = 10000;
  private final Set<VirtualFile> filesToAnalyze = new HashSet<>();
  @Mock
  private ProgressIndicator progress;
  @Mock
  private SonarLintAnalyzer sonarLintAnalyzer;
  @Mock
  private AnalysisResults analysisResults;
  private final SonarLintConsole sonarLintConsole = mock(SonarLintConsole.class);
  private final FindingsCache findingsCacheMock = mock(FindingsCache.class);

  @BeforeEach
  void prepare() {
    MockitoAnnotations.openMocks(this);
    var testFile = myFixture.configureByText("MyClass.java", "class MyClass {]");
    filesToAnalyze.add(testFile.getVirtualFile());
    when(progress.isCanceled()).thenReturn(false);
    when(analysisResults.failedAnalysisFiles()).thenReturn(Collections.emptyList());
    var moduleAnalysisResult = new ModuleAnalysisResult(analysisResults.failedAnalysisFiles());
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class), any(ClientProgressMonitor.class)))
      .thenReturn(moduleAnalysisResult);
    when(findingsCacheMock.getSnapshot(any())).thenReturn(new CachedFindings(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet()));

    replaceProjectService(AnalysisStatus.class, new AnalysisStatus(getProject()));
    replaceProjectService(SonarLintAnalyzer.class, sonarLintAnalyzer);
    replaceProjectService(SonarLintConsole.class, sonarLintConsole);
    replaceProjectService(FindingsCache.class, findingsCacheMock);

    task = new Analysis(getProject(), filesToAnalyze, TriggerType.CURRENT_FILE_ACTION, mock(AnalysisCallback.class));

    // IntelliJ light test fixtures appear to reuse the same project container, so we need to ensure that status is stopped.
    AnalysisStatus.get(getProject()).stopRun();
    waitForReadinessAndResetMockInvocations();
  }

  @AfterEach
  void close() throws Exception {
    MockitoAnnotations.openMocks(this).close();
  }

  @Test
  void testTask() {
    task.run(progress);
    verify(sonarLintAnalyzer, timeout(analysisTaskTimeout)).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class),
      any(ClientProgressMonitor.class));

    assertThat(getExternalAnnotators())
      .extracting("implementationClass")
      .contains("org.sonarlint.intellij.editor.SonarExternalAnnotator");
    verifyNoMoreInteractions(sonarLintAnalyzer);
  }

  @Test
  void shouldIgnoreProjectLevelIssues() {
    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class), any(ClientProgressMonitor.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        RawIssueListener issueListener = invocation.getArgument(2);
        RawIssue issue = SonarLintTestUtils.createIssue(1);
        issueListener.handle(issue);
        return analysisResults;
      });

    task.run(progress);

    verify(sonarLintAnalyzer, timeout(analysisTaskTimeout)).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class),
      any(ClientProgressMonitor.class));
    verify(findingsCacheMock, never()).replaceFindings(any());
  }

  @Test
  void shouldIgnoreInvalidFiles() {
    var vFile = filesToAnalyze.iterator().next();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      try {
        vFile.delete(null);
      } catch (IOException e) {
        Assertions.fail("Cannot delete file");
      }
    });
    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);

    task.run(progress);

    verifyNoInteractions(sonarLintAnalyzer);
    verify(findingsCacheMock, never()).replaceFindings(any());
  }

  @Test
  void shouldReportIssueForValidFile() {
    var issue = mock(RawIssue.class);
    var clientInputFile = mock(ClientInputFile.class);
    when(issue.getInputFile()).thenReturn(clientInputFile);
    var virtualFile = filesToAnalyze.iterator().next();
    when(clientInputFile.getClientObject()).thenReturn(virtualFile);
    when(clientInputFile.getPath()).thenReturn("path");

    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class), any(ClientProgressMonitor.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        RawIssueListener issueListener = invocation.getArgument(2);
        issueListener.handle(issue);
        return analysisResults;
      });

    task.run(progress);

    verify(sonarLintAnalyzer, timeout(analysisTaskTimeout)).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class),
      any(ClientProgressMonitor.class));
  }

  @Test
  void shouldSkipIfFailedToFindIssueLocation() {
    var issue = buildValidIssue();

    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class), any(ClientProgressMonitor.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        RawIssueListener issueListener = invocation.getArgument(2);
        issueListener.handle(issue);
        return analysisResults;
      });

    task.run(progress);

    verify(sonarLintAnalyzer, timeout(analysisTaskTimeout)).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class),
      any(ClientProgressMonitor.class));
  }

  @Test
  void testCancel() {
    task.cancel();
    task.run(progress);

    verify(sonarLintConsole, timeout(analysisTaskTimeout)).info("Analysis canceled");
  }

  private List<LanguageExtensionPoint<?>> getExternalAnnotators() {
    ExtensionPoint<LanguageExtensionPoint<?>> extensionPoint = Extensions.getRootArea().getExtensionPoint("com.intellij.externalAnnotator");
    return extensionPoint.extensions().toList();
  }

  // Readiness check happens once for the all the tests under SonarLintLightTests
  // If multiple tests are run, only the first test will wait for it
  private void waitForReadinessAndResetMockInvocations() {
    var wasAnalysisReady = getService(getProject(), AnalysisReadinessCache.class).isReady();
    Awaitility.await().atMost(25, TimeUnit.SECONDS).untilAsserted(() ->
      assertThat(getService(getProject(), AnalysisReadinessCache.class).isReady()).isTrue());
    if (!wasAnalysisReady) {
      verify(sonarLintAnalyzer, timeout(analysisTaskTimeout)).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(RawIssueListener.class),
        any(ClientProgressMonitor.class));
      clearInvocations(sonarLintAnalyzer);
      clearInvocations(sonarLintConsole);
      clearInvocations(findingsCacheMock);
    }
  }

  @NotNull
  private RawIssue buildValidIssue() {
    var issue = mock(RawIssue.class);
    var clientInputFile = mock(ClientInputFile.class);
    when(issue.getInputFile()).thenReturn(clientInputFile);
    var virtualFile = filesToAnalyze.iterator().next();
    when(clientInputFile.getClientObject()).thenReturn(virtualFile);
    when(clientInputFile.getPath()).thenReturn("path");
    return issue;
  }
}
