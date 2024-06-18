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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.VirtualFileUtils;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AnalysisTests extends AbstractSonarLintLightTests {
  private Analysis task;
  private final Set<VirtualFile> filesToAnalyze = new HashSet<>();
  private URI fileUri;
  @Mock
  private ProgressIndicator progress;
  @Mock
  private SonarLintAnalyzer sonarLintAnalyzer;
  @Mock
  private AnalysisResults analysisResults;
  @Mock
  private BackendService backendService;
  @Mock
  private AnalysisReadinessCache analysisReadinessCache;
  private final SonarLintConsole sonarLintConsole = mock(SonarLintConsole.class);

  @BeforeEach
  void prepare() {
    MockitoAnnotations.openMocks(this);
    replaceProjectService(BackendService.class, backendService);
    var testFile = myFixture.configureByText("MyClass.java", "class MyClass {]");
    this.fileUri = VirtualFileUtils.INSTANCE.toURI(testFile.getVirtualFile());
    filesToAnalyze.add(testFile.getVirtualFile());
    when(progress.isCanceled()).thenReturn(false);
    when(analysisResults.failedAnalysisFiles()).thenReturn(Collections.emptyList());
    var moduleAnalysisResult = new ModuleAnalysisResult(analysisResults.failedAnalysisFiles());
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(AnalysisState.class), any(ProgressIndicator.class), any(Boolean.class)))
      .thenReturn(moduleAnalysisResult);

    analysisReadinessCache.setReady(true);
    replaceModuleService(AnalysisReadinessCache.class, analysisReadinessCache);
    replaceProjectService(AnalysisStatus.class, new AnalysisStatus(getProject()));
    replaceProjectService(SonarLintAnalyzer.class, sonarLintAnalyzer);
    replaceProjectService(SonarLintConsole.class, sonarLintConsole);

    task = new Analysis(getProject(), filesToAnalyze, TriggerType.CURRENT_FILE_ACTION, mock(AnalysisCallback.class));

    // IntelliJ light test fixtures appear to reuse the same project container, so we need to ensure that status is stopped.
    AnalysisStatus.get(getProject()).stopRun();
  }

  @AfterEach
  void close() throws Exception {
    MockitoAnnotations.openMocks(this).close();
  }

  @Test
  void testTask() {
    task.run(progress);
    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(AnalysisState.class),
      any(ProgressIndicator.class), any(Boolean.class));

    assertThat(getExternalAnnotators())
      .extracting("implementationClass")
      .contains("org.sonarlint.intellij.editor.SonarExternalAnnotator");
    verifyNoMoreInteractions(sonarLintAnalyzer);
  }

  @Test
  void shouldIgnoreProjectLevelIssues() {
    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(AnalysisState.class), any(ProgressIndicator.class), any(Boolean.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        AnalysisState analysisState = invocation.getArgument(2);
        var issue = SonarLintTestUtils.createIssue(1);
        analysisState.addRawIssues(Map.of(fileUri, List.of(issue)), false);
        return analysisResults;
      });

    clearInvocations(sonarLintAnalyzer);
    task.run(progress);

    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(AnalysisState.class),
      any(ProgressIndicator.class), any(Boolean.class));
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

    clearInvocations(sonarLintAnalyzer);
    task.run(progress);

    verifyNoInteractions(sonarLintAnalyzer);
  }

  @Test
  void shouldReportIssueForValidFile() {
    var issue = mock(RaisedIssueDto.class);

    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(AnalysisState.class), any(ProgressIndicator.class), any(Boolean.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        AnalysisState analysisState = invocation.getArgument(2);
        analysisState.addRawIssues(Map.of(fileUri, List.of(issue)), false);
        return analysisResults;
      });

    clearInvocations(sonarLintAnalyzer);
    task.run(progress);

    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(AnalysisState.class),
      any(ProgressIndicator.class), any(Boolean.class));
  }

  @Test
  void shouldSkipIfFailedToFindIssueLocation() {
    var issue = mock(RaisedIssueDto.class);

    var listener = mock(AnalysisListener.class);
    getProject().getMessageBus().connect(getProject()).subscribe(AnalysisListener.TOPIC, listener);
    when(sonarLintAnalyzer.analyzeModule(eq(getModule()), eq(filesToAnalyze), any(AnalysisState.class), any(ProgressIndicator.class), any(Boolean.class)))
      .thenAnswer((Answer<AnalysisResults>) invocation -> {
        AnalysisState analysisState = invocation.getArgument(2);
        analysisState.addRawIssues(Map.of(fileUri, List.of(issue)), false);
        return analysisResults;
      });

    clearInvocations(sonarLintAnalyzer);
    task.run(progress);

    verify(sonarLintAnalyzer).analyzeModule(eq(getModule()), eq(filesToAnalyze), any(AnalysisState.class), any(ProgressIndicator.class), any(Boolean.class));
  }

  @Test
  void testCancel() {
    task.cancel();
    task.run(progress);

    verify(sonarLintConsole).info("Analysis canceled");
  }

  private List<LanguageExtensionPoint<?>> getExternalAnnotators() {
    ExtensionPoint<LanguageExtensionPoint<?>> extensionPoint = Extensions.getRootArea().getExtensionPoint("com.intellij.externalAnnotator");
    return extensionPoint.extensions().toList();
  }

}
