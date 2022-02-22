/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class EditorOpenTriggerTest extends AbstractSonarLintLightTests {
  private SonarLintSubmitter submitter = mock(SonarLintSubmitter.class);

  private EditorOpenTrigger editorTrigger;
  private VirtualFile file;
  private FileEditorManager editorManager;

  @Before
  public void start() {
    editorTrigger = new EditorOpenTrigger();
    getGlobalSettings().setAutoTrigger(true);
    replaceProjectService(SonarLintSubmitter.class, submitter);

    file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "public class MyClass{}");
    editorManager = mock(FileEditorManager.class);
    when(editorManager.getProject()).thenReturn(getProject());
  }

  @Test
  public void should_trigger() {
    editorTrigger.fileOpened(editorManager, file);

    verify(submitter).submitFiles(Collections.singleton(file), TriggerType.EDITOR_OPEN, true);
  }

  @Test
  public void should_not_trigger_if_auto_disabled() {
    getGlobalSettings().setAutoTrigger(false);

    editorTrigger.fileOpened(editorManager, file);

    verifyZeroInteractions(submitter);
  }

  @Test
  public void should_do_nothing_closed() {
    editorTrigger.fileClosed(editorManager, file);
    editorTrigger.selectionChanged(new FileEditorManagerEvent(editorManager, null, null, null, null));

    verifyZeroInteractions(submitter);
  }
}
