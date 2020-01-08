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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.issue.AnalysisResultIssues;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLintCheckinHandlerTest extends SonarTest {
  private SonarLintCheckinHandler handler;
  private CompletableFuture<Void> future = new CompletableFuture<>();
  private SonarLintGlobalSettings globalSettings = new SonarLintGlobalSettings();

  private VirtualFile file = mock(VirtualFile.class);
  private SonarLintSubmitter submitter = mock(SonarLintSubmitter.class);
  private AnalysisResultIssues analysisResultIssues = mock(AnalysisResultIssues.class);
  private IssueManager issueManager = mock(IssueManager.class);
  private CheckinProjectPanel checkinProjectPanel = mock(CheckinProjectPanel.class);

  @Before
  public void prepare() {
    super.register(project, SonarLintSubmitter.class, submitter);
    super.register(project, AnalysisResultIssues.class, analysisResultIssues);
    super.register(project, IssueManager.class, issueManager);

    when(checkinProjectPanel.getVirtualFiles()).thenReturn(Collections.singleton(file));
  }

  @Test
  public void testNoUnresolvedIssues() {
    future.complete(null);
    LiveIssue issue = mock(LiveIssue.class);
    when(issue.isResolved()).thenReturn(true);

    when(issueManager.getForFile(file)).thenReturn(Collections.singleton(issue));

    handler = new SonarLintCheckinHandler(globalSettings, project, checkinProjectPanel);
    CheckinHandler.ReturnResult result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.COMMIT);
    verify(analysisResultIssues).set(Collections.singletonMap(file, Collections.singleton(issue)), "SCM changed files");
    verify(submitter).submitFilesModal(eq(Collections.singleton(file)), eq(TriggerType.CHECK_IN), any(AnalysisCallback.class));
  }

  @Test
  public void testIssues() {
    future.complete(null);
    LiveIssue issue = mock(LiveIssue.class);

    when(issueManager.getForFile(file)).thenReturn(Collections.singleton(issue));

    handler = new SonarLintCheckinHandler(globalSettings, project, checkinProjectPanel);
    CheckinHandler.ReturnResult result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.CANCEL);
    verify(analysisResultIssues).set(anyMap(), eq("SCM changed files"));
    verify(submitter).submitFilesModal(eq(Collections.singleton(file)), eq(TriggerType.CHECK_IN), any(AnalysisCallback.class));
  }
}
