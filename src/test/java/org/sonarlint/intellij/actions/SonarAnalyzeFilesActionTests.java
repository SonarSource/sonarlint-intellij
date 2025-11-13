/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisReadinessCache;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

class SonarAnalyzeFilesActionTests extends AbstractSonarLintLightTests {
  private static final UUID RANDOM_UUID = UUID.randomUUID();
  private AnalysisSubmitter analysisSubmitter = mock(AnalysisSubmitter.class);
  private AnActionEvent event = mock(AnActionEvent.class);

  private Presentation presentation = new Presentation();
  private SonarAnalyzeFilesAction editorFileAction = new SonarAnalyzeFilesAction();

  @BeforeEach
  void prepare() {
    Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
      assertThat(getService(getProject(), AnalysisReadinessCache.class).isModuleReady(getModule())).isTrue()
    );

    replaceProjectService(AnalysisSubmitter.class, analysisSubmitter);
    when(event.getProject()).thenReturn(getProject());
    when(event.getPresentation()).thenReturn(presentation);

    clearInvocations(analysisSubmitter);
  }

  @Test
  void should_submit() {
    VirtualFile f1 = myFixture.copyFileToProject("foo.php", "foo.php");
    mockSelectedFiles(f1);
    editorFileAction.actionPerformed(event);
    verify(analysisSubmitter, timeout(2000)).analyzeFilesOnUserAction(anySet(), eq(event));
  }

  private void mockSelectedFiles(VirtualFile file) {
    when(event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file});
  }

  @Test
  void should_do_nothing_if_no_file() {
    editorFileAction.actionPerformed(event);
    verifyNoInteractions(analysisSubmitter);
  }

  @Test
  void should_do_nothing_if_no_project() {
    when(event.getProject()).thenReturn(null);
    editorFileAction.actionPerformed(event);
    verifyNoInteractions(analysisSubmitter);
  }

  @Test
  void should_be_enabled_if_file_and_not_running() {
    VirtualFile f1 = myFixture.copyFileToProject("foo.php", "foo.php");
    mockSelectedFiles(f1);

    AnalysisStatus.get(getProject()).tryRun(RANDOM_UUID);

    editorFileAction.update(event);
    assertThat(presentation.isEnabled()).isFalse();

    AnalysisStatus.get(getProject()).stopRun(RANDOM_UUID);
    editorFileAction.update(event);
    assertThat(presentation.isEnabled()).isTrue();
  }
}
