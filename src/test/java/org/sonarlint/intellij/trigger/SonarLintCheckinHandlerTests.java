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
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.AnalysisState;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.analysis.RunningAnalysesTracker;
import org.sonarlint.intellij.callable.CheckInCallable;
import org.sonarlint.intellij.finding.LiveFindings;
import org.sonarlint.intellij.finding.issue.LiveIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SonarLintCheckinHandlerTests extends AbstractSonarLintLightTests {

  private final VirtualFile file = mock(VirtualFile.class);
  private final AnalysisSubmitter analysisSubmitter = mock(AnalysisSubmitter.class);
  private final RunningAnalysesTracker runningAnalysesTracker = mock(RunningAnalysesTracker.class);
  private final SonarLintToolWindow toolWindow = mock(SonarLintToolWindow.class);
  private final CheckinProjectPanel checkinProjectPanel = mock(CheckinProjectPanel.class);
  private final CheckInCallable checkInCallable = mock(CheckInCallable.class);
  private SonarLintCheckinHandler handler;

  @BeforeEach
  void prepare() {
    replaceProjectService(AnalysisSubmitter.class, analysisSubmitter);
    replaceProjectService(RunningAnalysesTracker.class, runningAnalysesTracker);
    replaceProjectService(SonarLintToolWindow.class, toolWindow);

    when(checkinProjectPanel.getVirtualFiles()).thenReturn(Collections.singleton(file));
  }

  @Test
  void testNoUnresolvedIssues() {
    var uuid = UUID.randomUUID();
    var analysisState = new AnalysisState(uuid, checkInCallable, Collections.singleton(file), getModule(), TriggerType.CHECK_IN, null);
    var issue = mock(LiveIssue.class);
    when(issue.isResolved()).thenReturn(true);
    when(checkInCallable.analysisSucceeded()).thenReturn(true);
    when(checkInCallable.getResults())
      .thenReturn(List.of(new AnalysisResult(null, new LiveFindings(Map.of(file, Set.of(issue)), Collections.emptyMap()), Set.of(file), TriggerType.CHECK_IN, Instant.now())));
    when(runningAnalysesTracker.getById(uuid)).thenReturn(analysisState);
    when(runningAnalysesTracker.getById(uuid)).thenReturn(null);
    when(analysisSubmitter.analyzeFilesPreCommit(Collections.singleton(file)))
      .thenReturn(Pair.of(checkInCallable, List.of(uuid)));

    handler = new SonarLintCheckinHandler(getProject(), checkinProjectPanel);
    var result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.COMMIT);
    verify(analysisSubmitter, timeout(1000)).analyzeFilesPreCommit(Collections.singleton(file));
    verifyNoInteractions(toolWindow);
  }

  @Test
  void testIssues() {
    var uuid = UUID.randomUUID();
    var analysisState = new AnalysisState(uuid, checkInCallable, Collections.singleton(file), getModule(), TriggerType.CHECK_IN, null);
    var issue = mock(LiveIssue.class);
    when(issue.getRuleKey()).thenReturn("java:S123");
    when(checkInCallable.analysisSucceeded()).thenReturn(true);
    when(checkInCallable.getResults())
      .thenReturn(List.of(new AnalysisResult(null, new LiveFindings(Map.of(file, Set.of(issue)), Collections.emptyMap()), Set.of(file), TriggerType.CHECK_IN, Instant.now())));
    when(runningAnalysesTracker.getById(uuid)).thenReturn(analysisState);
    when(runningAnalysesTracker.getById(uuid)).thenReturn(null);
    when(analysisSubmitter.analyzeFilesPreCommit(Collections.singleton(file)))
      .thenReturn(Pair.of(checkInCallable, List.of(uuid)));

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
    var uuid = UUID.randomUUID();
    var analysisState = new AnalysisState(uuid, checkInCallable, Collections.singleton(file), getModule(), TriggerType.CHECK_IN, null);
    var issue = mock(LiveIssue.class);
    when(issue.getRuleKey()).thenReturn("secrets:S123");
    when(checkInCallable.analysisSucceeded()).thenReturn(true);
    when(checkInCallable.getResults())
      .thenReturn(List.of(new AnalysisResult(null, new LiveFindings(Map.of(file, Set.of(issue)), Collections.emptyMap()), Set.of(file), TriggerType.CHECK_IN, Instant.now())));
    when(runningAnalysesTracker.getById(uuid)).thenReturn(analysisState);
    when(runningAnalysesTracker.getById(uuid)).thenReturn(null);
    when(analysisSubmitter.analyzeFilesPreCommit(Collections.singleton(file)))
      .thenReturn(Pair.of(checkInCallable, List.of(uuid)));

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
}
