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

import com.intellij.lang.Language;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EditorOpenTriggerTests extends AbstractSonarLintLightTests {
  private AnalysisSubmitter analysisSubmitter = mock(AnalysisSubmitter.class);

  private EditorOpenTrigger editorTrigger;
  private VirtualFile file;
  private VirtualFile file2;
  private FileEditorManager editorManager;

  @BeforeEach
  void start() {
    editorTrigger = new EditorOpenTrigger(getProject());
    getGlobalSettings().setAutoTrigger(true);
    replaceProjectService(AnalysisSubmitter.class, analysisSubmitter);

    file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "class MyClass{}");
    file2 = createAndOpenTestVirtualFile("MyClass2.java", Language.findLanguageByID("JAVA"), "class MyClass2{}");
    editorManager = mock(FileEditorManager.class);
    when(editorManager.getProject()).thenReturn(getProject());
    reset(analysisSubmitter);
    editorTrigger.onProjectOpened();
  }

  @AfterEach
  void cleanup() {
    editorTrigger.dispose();
  }

  @Test
  void should_trigger() {
    editorTrigger.fileOpened(editorManager, file);

    verify(analysisSubmitter, timeout(3000)).autoAnalyzeFiles(List.of(file), TriggerType.EDITOR_OPEN);
  }

  @Test
  void should_trigger_same_time_for_multiple_files() {
    editorTrigger.fileOpened(editorManager, file);
    editorTrigger.fileOpened(editorManager, file2);

    verify(analysisSubmitter, timeout(3000)).autoAnalyzeFiles(List.of(file, file2), TriggerType.EDITOR_OPEN);
  }

  @Test
  void should_do_nothing_closed() {
    editorTrigger.fileClosed(editorManager, file);
    editorTrigger.selectionChanged(new FileEditorManagerEvent(editorManager, null, null, null, null));

    verifyNoInteractions(analysisSubmitter);
  }
}
