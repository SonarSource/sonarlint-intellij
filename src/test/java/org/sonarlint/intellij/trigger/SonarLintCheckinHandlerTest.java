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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.issue.LiveIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLintCheckinHandlerTest extends SonarTest {
  private SonarLintCheckinHandler handler;
  private CompletableFuture<AnalysisResult> future;
  private SonarLintGlobalSettings globalSettings;
  @Mock
  private VirtualFile file;
  @Mock
  private SonarLintSubmitter submitter;
  @Mock
  private ChangedFilesIssues changedFilesIssues;

  @Before
  public void setUp() {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    globalSettings = new SonarLintGlobalSettings();
    future = new CompletableFuture<>();
    when(submitter.submitFiles(new VirtualFile[] {file}, TriggerType.CHECK_IN, false, true)).thenReturn(future);

    super.register(project, SonarLintSubmitter.class, submitter);
    super.register(project, ChangedFilesIssues.class, changedFilesIssues);
  }

  @Test
  public void testNoIssues() {
    future.complete(new AnalysisResult(0, Collections.emptyMap()));

    handler = new SonarLintCheckinHandler(mock(ToolWindowManager.class), globalSettings, Collections.singleton(file), project);
    CheckinHandler.ReturnResult result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.COMMIT);
    verify(changedFilesIssues).set(Collections.emptyMap());
    verify(submitter).submitFiles(new VirtualFile[] {file}, TriggerType.CHECK_IN, false, true);
  }

  @Test
  public void testIssues() {
    future.complete(new AnalysisResult(2, Collections.singletonMap(file, Collections.singletonList(mock(LiveIssue.class)))));

    handler = new SonarLintCheckinHandler(mock(ToolWindowManager.class), globalSettings, Collections.singleton(file), project);
    CheckinHandler.ReturnResult result = handler.beforeCheckin(null, null);

    assertThat(result).isEqualTo(CheckinHandler.ReturnResult.CANCEL);
    verify(changedFilesIssues).set(anyMap());
    verify(submitter).submitFiles(new VirtualFile[] {file}, TriggerType.CHECK_IN, false, true);
  }
}
