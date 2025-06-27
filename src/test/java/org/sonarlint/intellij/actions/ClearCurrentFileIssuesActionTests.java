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
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.analysis.OnTheFlyFindingsHolder;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.LiveFindings;
import org.sonarlint.intellij.finding.issue.LiveIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClearCurrentFileIssuesActionTests extends AbstractSonarLintLightTests {

  private final ClearCurrentFileIssuesAction clearIssues = new ClearCurrentFileIssuesAction(null, null, null);
  private final AnActionEvent event = mock(AnActionEvent.class);
  private OnTheFlyFindingsHolder findingsHolder;
  private VirtualFile file;

  @BeforeEach
  void prepare() {
    when(event.getProject()).thenReturn(getProject());
    file = myFixture.copyFileToProject("foo.php", "foo.php");
    findingsHolder = SonarLintUtils.getService(getProject(), AnalysisSubmitter.class).getOnTheFlyFindingsHolder();
    findingsHolder.clearCurrentFile();
    FileEditorManager.getInstance(getProject()).openFile(file, true);
    findingsHolder.updateOnAnalysisResult(new AnalysisResult(null, new LiveFindings(Map.of(file, List.of(mock(LiveIssue.class))), Collections.emptyMap()), List.of(file), Instant.now()));
  }

  @Test
  void testClear() {
    clearIssues.actionPerformed(event);

    assertThat(findingsHolder.getIssuesForFile(file)).isEmpty();
  }

  @Test
  void testClearWithInvalidFiles() throws IOException {
    WriteAction.run(() -> file.delete(getProject()));

    clearIssues.actionPerformed(event);

    assertThat(findingsHolder.getIssuesForFile(file)).isEmpty();
  }

  @Test
  void testDoNothingIfNoProject() {
    var file2 = myFixture.copyFileToProject("foo2.php", "foo2.php");
    FileEditorManager.getInstance(getProject()).openFile(file2, true);

    clearIssues.actionPerformed(event);

    assertThat(findingsHolder.getIssuesForFile(file)).isNotEmpty();
  }

}
