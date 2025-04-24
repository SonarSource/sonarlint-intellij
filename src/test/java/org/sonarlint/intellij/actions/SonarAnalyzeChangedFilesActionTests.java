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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SonarAnalyzeChangedFilesActionTests extends AbstractSonarLintLightTests {

  private final AnActionEvent event = mock(AnActionEvent.class);
  private final AnalysisSubmitter analysisSubmitter = mock(AnalysisSubmitter.class);
  private final ChangeListManager changeListManager = mock(ChangeListManager.class);
  private final SonarLintTelemetry sonarLintTelemetry = mock(SonarLintTelemetry.class);

  private SonarAnalyzeChangedFilesAction action;
  private AnalysisStatus status;

  @BeforeEach
  void before() {
    action = new SonarAnalyzeChangedFilesAction();
    status = AnalysisStatus.get(getProject());
    replaceProjectService(AnalysisSubmitter.class, analysisSubmitter);
    replaceProjectService(ChangeListManager.class, changeListManager);
    replaceApplicationService(SonarLintTelemetry.class, sonarLintTelemetry);
  }

  @Test
  void testEnabled() {
    status.tryRun();
    assertThat(action.isEnabled(event, getProject(), status)).isFalse();

    status.stopRun();
    when(changeListManager.getAffectedFiles()).thenReturn(Collections.emptyList());
    assertThat(action.isEnabled(event, getProject(), status)).isFalse();

    status.stopRun();
    when(changeListManager.getAffectedFiles()).thenReturn(List.of(mock(VirtualFile.class)));
    assertThat(action.isEnabled(event, getProject(), status)).isTrue();
  }

  @Test
  void testNoProject() {
    when(event.getProject()).thenReturn(null);

    action.actionPerformed(event);

    verifyNoInteractions(analysisSubmitter);
  }

  @Test
  void testRun() {
    var file = myFixture.copyFileToProject("foo.php", "foo.php");
    when(event.getProject()).thenReturn(getProject());
    when(changeListManager.getAffectedFiles()).thenReturn(List.of(file));

    action.actionPerformed(event);

    verify(analysisSubmitter, timeout(1000)).analyzeVcsChangedFiles();
  }

  @Test
  void testTelemetryIsSent() {
    VirtualFile file = myFixture.copyFileToProject("foo.php", "foo.php");
    when(event.getProject()).thenReturn(getProject());
    when(changeListManager.getAffectedFiles()).thenReturn(List.of(file));

    action.actionPerformed(event);

    verify(analysisSubmitter, timeout(1000)).analyzeVcsChangedFiles();
    verify(sonarLintTelemetry, timeout(1000)).analysisReportingTriggered(AnalysisReportingType.VCS_CHANGED_ANALYSIS_TYPE);
  }

}
