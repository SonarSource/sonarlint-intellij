/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.analysis.AnalysisReadinessCache;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.AnalysisState;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.analysis.RunningAnalysesTracker;
import org.sonarlint.intellij.callable.CheckInCallable;
import org.sonarlint.intellij.finding.LiveFindings;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

class SonarLintCheckinHandlerTests extends AbstractSonarLintLightTests {

  private final VirtualFile file = mock(VirtualFile.class);
  private final AnalysisSubmitter analysisSubmitter = mock(AnalysisSubmitter.class);
  private final RunningAnalysesTracker runningAnalysesTracker = mock(RunningAnalysesTracker.class);
  private final SonarLintToolWindow toolWindow = mock(SonarLintToolWindow.class);
  private final CheckinProjectPanel checkinProjectPanel = mock(CheckinProjectPanel.class);
  private final CheckInCallable checkInCallable = mock(CheckInCallable.class);
  private final SonarLintTelemetry sonarLintTelemetry = mock(SonarLintTelemetry.class);

  private SonarLintCheckinHandler handler;
  private UUID analysisUuid;

  @BeforeEach
  void prepare() {
    replaceProjectService(AnalysisSubmitter.class, analysisSubmitter);
    replaceProjectService(RunningAnalysesTracker.class, runningAnalysesTracker);
    replaceProjectService(SonarLintToolWindow.class, toolWindow);
    replaceApplicationService(SonarLintTelemetry.class, sonarLintTelemetry);

    when(checkinProjectPanel.getVirtualFiles()).thenReturn(Collections.singleton(file));
    analysisUuid = UUID.randomUUID();
    when(analysisSubmitter.analyzeFilesPreCommit(Collections.singleton(file)))
      .thenReturn(Pair.of(checkInCallable, List.of(analysisUuid)));
    Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
      assertThat(getService(getProject(), AnalysisReadinessCache.class).isModuleReady(getModule())).isTrue()
    );
    clearInvocations(analysisSubmitter);
  }

  @Test
  void testNoUnresolvedIssues() {
    var analysisState = new AnalysisState(analysisUuid, checkInCallable, Collections.singleton(file), getModule(), TriggerType.CHECK_IN, null);
    var issue = mock(LiveIssue.class);
    when(issue.isResolved()).thenReturn(true);
    when(checkInCallable.analysisSucceeded()).thenReturn(true);
    when(checkInCallable.getResults())
      .thenReturn(List.of(new AnalysisResult(null, new LiveFindings(Map.of(file, Set.of(issue)), Collections.emptyMap()), Set.of(file), TriggerType.CHECK_IN, Instant.now())));
    when(runningAnalysesTracker.getById(analysisUuid)).thenReturn(analysisState);
    when(runningAnalysesTracker.getById(analysisUuid)).thenReturn(null);

    handler = new SonarLintCheckinHandler(getProject(), checkinProjectPanel);
    var result = handler.beforeCheckin(null, null);

    verify(analysisSubmitter, timeout(1000)).analyzeFilesPreCommit(Collections.singleton(file));
    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.COMMIT);
  }

  @Test
  void testIssues() {
    var analysisState = new AnalysisState(analysisUuid, checkInCallable, Collections.singleton(file), getModule(), TriggerType.CHECK_IN, null);
    var issue = mock(LiveIssue.class);
    when(issue.getRuleKey()).thenReturn("java:S123");
    when(checkInCallable.analysisSucceeded()).thenReturn(true);
    when(checkInCallable.getResults())
      .thenReturn(List.of(new AnalysisResult(null, new LiveFindings(Map.of(file, Set.of(issue)), Collections.emptyMap()), Set.of(file), TriggerType.CHECK_IN, Instant.now())));
    when(runningAnalysesTracker.getById(analysisUuid)).thenReturn(analysisState);
    when(runningAnalysesTracker.getById(analysisUuid)).thenReturn(null);

    handler = new SonarLintCheckinHandler(getProject(), checkinProjectPanel);
    var messages = new ArrayList<>();
    TestDialogManager.setTestDialog(msg -> {
      messages.add(msg);
      return Messages.OK;
    });
    var result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.CLOSE_WINDOW);
    assertThat(messages).containsExactly("SonarQube for IDE analysis on 1 file found 1 issue");
    ArgumentCaptor<AnalysisResult> analysisResultCaptor = ArgumentCaptor.forClass(AnalysisResult.class);
    verify(toolWindow, timeout(1000)).openReportTab(analysisResultCaptor.capture());
    var analysisResult = analysisResultCaptor.getValue();
    assertThat(analysisResult.getFindings().getIssuesPerFile()).containsEntry(file, Set.of(issue));
    verify(analysisSubmitter, timeout(1000)).analyzeFilesPreCommit(Collections.singleton(file));
  }

  @Test
  void testSecretsIssues() {
    var analysisState = new AnalysisState(analysisUuid, checkInCallable, Collections.singleton(file), getModule(), TriggerType.CHECK_IN, null);
    var issue = mock(LiveIssue.class);
    when(issue.getRuleKey()).thenReturn("secrets:S123");
    when(checkInCallable.analysisSucceeded()).thenReturn(true);
    when(checkInCallable.getResults())
      .thenReturn(List.of(new AnalysisResult(null, new LiveFindings(Map.of(file, Set.of(issue)), Collections.emptyMap()), Set.of(file), TriggerType.CHECK_IN, Instant.now())));
    when(runningAnalysesTracker.getById(analysisUuid)).thenReturn(analysisState);
    when(runningAnalysesTracker.getById(analysisUuid)).thenReturn(null);

    handler = new SonarLintCheckinHandler(getProject(), checkinProjectPanel);
    var messages = new ArrayList<>();
    TestDialogManager.setTestDialog(msg -> {
      messages.add(msg);
      return Messages.OK;
    });
    var result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.CLOSE_WINDOW);
    assertThat(messages).containsExactly("""
      SonarQube for IDE analysis on 1 file found 1 issue

      SonarQube for IDE analysis found 1 secret. Committed secrets may lead to unauthorized system access.""");
    ArgumentCaptor<AnalysisResult> analysisResultCaptor = ArgumentCaptor.forClass(AnalysisResult.class);
    verify(toolWindow, timeout(1000)).openReportTab(analysisResultCaptor.capture());
    var analysisResult = analysisResultCaptor.getValue();
    assertThat(analysisResult.getFindings().getIssuesPerFile()).containsEntry(file, Set.of(issue));
    verify(analysisSubmitter, timeout(1000)).analyzeFilesPreCommit(Collections.singleton(file));
  }

  @Test
  void testTelemetryIsSent() {
    var analysisState = new AnalysisState(analysisUuid, checkInCallable, Collections.singleton(file), getModule(), TriggerType.CHECK_IN, null);
    var issue = mock(LiveIssue.class);
    when(issue.isResolved()).thenReturn(true);
    when(checkInCallable.analysisSucceeded()).thenReturn(true);
    when(checkInCallable.getResults())
      .thenReturn(List.of(new AnalysisResult(null, new LiveFindings(Map.of(file, Set.of(issue)), Collections.emptyMap()), Set.of(file), TriggerType.CHECK_IN, Instant.now())));
    when(runningAnalysesTracker.getById(analysisUuid)).thenReturn(analysisState);
    when(runningAnalysesTracker.getById(analysisUuid)).thenReturn(null);

    handler = new SonarLintCheckinHandler(getProject(), checkinProjectPanel);
    var result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.COMMIT);
    verify(analysisSubmitter, timeout(1000)).analyzeFilesPreCommit(Collections.singleton(file));
    verify(sonarLintTelemetry, timeout(1000)).analysisReportingTriggered(AnalysisReportingType.PRE_COMMIT_ANALYSIS_TYPE);
  }

}
