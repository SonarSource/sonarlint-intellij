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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class SonarLintCheckinHandlerTest extends AbstractSonarLintLightTests {
  private SonarLintCheckinHandler handler;
  private CompletableFuture<Void> future = new CompletableFuture<>();

  private VirtualFile file = mock(VirtualFile.class);
  private AnalysisSubmitter analysisSubmitter = mock(AnalysisSubmitter.class);
  private SonarLintToolWindow toolWindow = mock(SonarLintToolWindow.class);
  private IssueManager issueManager = mock(IssueManager.class);
  private CheckinProjectPanel checkinProjectPanel = mock(CheckinProjectPanel.class);

  @Before
  public void prepare() {
    replaceProjectService(AnalysisSubmitter.class, analysisSubmitter);
    replaceProjectService(SonarLintToolWindow.class, toolWindow);
    replaceProjectService(IssueManager.class, issueManager);

    when(checkinProjectPanel.getVirtualFiles()).thenReturn(Collections.singleton(file));
    when(analysisSubmitter.analyzeFilesPreCommit(Collections.singleton(file))).thenReturn(true);
  }

  @Test
  public void testNoUnresolvedIssues() {
    future.complete(null);
    var issue = mock(LiveIssue.class);
    when(issue.isResolved()).thenReturn(true);

    when(issueManager.getForFile(file)).thenReturn(Collections.singleton(issue));

    handler = new SonarLintCheckinHandler(getProject(), checkinProjectPanel);
    var result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.COMMIT);
    verify(analysisSubmitter).analyzeFilesPreCommit(Collections.singleton(file));
    verifyNoInteractions(toolWindow);
  }

  @Test
  public void testIssues() {
    future.complete(null);
    var issue = mock(LiveIssue.class);
    when(issue.getRuleKey()).thenReturn("java:S123");

    when(issueManager.getForFile(file)).thenReturn(Collections.singleton(issue));

    handler = new SonarLintCheckinHandler(getProject(), checkinProjectPanel);
    var messages = new ArrayList<>();
    TestDialogManager.setTestDialog(msg -> {
      messages.add(msg);
      return Messages.OK;
    });
    var result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.CLOSE_WINDOW);
    assertThat(messages).containsExactly("SonarLint analysis on 1 file found 1 issue");
    ArgumentCaptor<AnalysisResult> analysisResultCaptor = ArgumentCaptor.forClass(AnalysisResult.class);
    verify(toolWindow).openReportTab(analysisResultCaptor.capture());
    var analysisResult = analysisResultCaptor.getValue();
    assertThat(analysisResult.getIssuesPerFile()).containsEntry(file, Set.of(issue));
    assertThat(analysisResult.getWhatAnalyzed()).isEqualTo("SCM changed files");
    verify(analysisSubmitter).analyzeFilesPreCommit(Collections.singleton(file));
  }

  @Test
  public void testSecretsIssues() {
    future.complete(null);
    var issue = mock(LiveIssue.class);
    when(issue.getRuleKey()).thenReturn("secrets:S123");

    when(issueManager.getForFile(file)).thenReturn(Collections.singleton(issue));

    handler = new SonarLintCheckinHandler(getProject(), checkinProjectPanel);
    var messages = new ArrayList<>();
    TestDialogManager.setTestDialog(msg -> {
      messages.add(msg);
      return Messages.OK;
    });
    var result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.CLOSE_WINDOW);
    assertThat(messages).containsExactly("SonarLint analysis on 1 file found 1 issue\n" +
      "\n" +
      "SonarLint analysis found 1 secret. Committed secrets may lead to unauthorized system access.");
    ArgumentCaptor<AnalysisResult> analysisResultCaptor = ArgumentCaptor.forClass(AnalysisResult.class);
    verify(toolWindow).openReportTab(analysisResultCaptor.capture());
    var analysisResult = analysisResultCaptor.getValue();
    assertThat(analysisResult.getIssuesPerFile()).containsEntry(file, Set.of(issue));
    assertThat(analysisResult.getWhatAnalyzed()).isEqualTo("SCM changed files");
    verify(analysisSubmitter).analyzeFilesPreCommit(Collections.singleton(file));
  }
}
