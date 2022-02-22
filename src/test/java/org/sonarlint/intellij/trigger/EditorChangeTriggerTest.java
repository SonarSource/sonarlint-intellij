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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class EditorChangeTriggerTest extends AbstractSonarLintLightTests {
  private final SonarLintSubmitter submitter = mock(SonarLintSubmitter.class);
  private final FileDocumentManager docManager = mock(FileDocumentManager.class);
  private EditorChangeTrigger underTest;

  @Before
  public void prepare() {
    replaceProjectService(SonarLintSubmitter.class, submitter);
    getGlobalSettings().setAutoTrigger(true);
    underTest = new EditorChangeTrigger(getProject());
    underTest.onProjectOpened();
  }

  @After
  public void cleanup() {
    underTest.dispose();
  }

  @Test
  public void should_trigger() {
    var file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    underTest.documentChanged(createEvent(file));

    assertThat(underTest.getEvents()).hasSize(1);
    verify(submitter, timeout(3000)).submitFiles(new ArrayList<>(Collections.singleton(file)), TriggerType.EDITOR_CHANGE, true);
    verifyNoMoreInteractions(submitter);
  }

  @Test
  public void should_trigger_multiple_files() {
    var file1 = createAndOpenTestVirtualFile("MyClass1.java", Language.findLanguageByID("JAVA"), "");
    var file2 = createAndOpenTestVirtualFile("MyClass2.java", Language.findLanguageByID("JAVA"), "");

    underTest.documentChanged(createEvent(file1));
    underTest.documentChanged(createEvent(file2));

    assertThat(underTest.getEvents()).hasSize(2);
    ArgumentCaptor<List<VirtualFile>> captor = ArgumentCaptor.forClass(List.class);
    verify(submitter, timeout(3000)).submitFiles(captor.capture(), eq(TriggerType.EDITOR_CHANGE), eq(true));
    assertThat(captor.getValue()).containsExactlyInAnyOrder(file1, file2);
  }

  @Test
  public void should_cancel_previous_task() {
    var file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    var analysisTask = mock(AnalysisTask.class);
    when(submitter.submitFiles(any(), any(), anyBoolean())).thenReturn(analysisTask);
    when(analysisTask.isFinished()).thenReturn(false);

    underTest.documentChanged(createEvent(file));
    // Two rapid changes should only lead to a single trigger
    underTest.documentChanged(createEvent(file));

    assertThat(underTest.getEvents()).hasSize(1);
    verify(submitter, timeout(3000)).submitFiles(new ArrayList<>(Collections.singleton(file)), TriggerType.EDITOR_CHANGE, true);

    // Schedule again
    underTest.documentChanged(createEvent(file));

    verify(analysisTask, timeout(3000)).cancel();
    when(analysisTask.isFinished()).thenReturn(true);
    verify(submitter, timeout(1000)).submitFiles(new ArrayList<>(Collections.singleton(file)), TriggerType.EDITOR_CHANGE, true);

    verifyNoMoreInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_auto_disabled() {
    var file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");
    getGlobalSettings().setAutoTrigger(false);

    underTest.documentChanged(createEvent(file));
    verifyZeroInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_check_fails() {
    var doc = mock(Document.class);
    var file = createTestFile("Foo.java", Language.findLanguageByID("JAVA"), "public class Foo {}");

    var event = createEvent(file);

    when(event.getDocument()).thenReturn(doc);
    when(docManager.getFile(doc)).thenReturn(file);
    underTest.documentChanged(event);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_project_is_closed() {
    var file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    underTest.documentChanged(createEvent(file));

    verifyZeroInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_no_vfile() {
    var file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    var doc = mock(Document.class);
    var event = createEvent(file);

    when(event.getDocument()).thenReturn(doc);
    when(docManager.getFile(doc)).thenReturn(null);

    underTest.documentChanged(event);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void nothing_to_do_before_doc_change() {
    underTest.beforeDocumentChange(null);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void clear_and_dispose() {
    var file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    underTest.documentChanged(createEvent(file));
    underTest.dispose();

    assertThat(underTest.getEvents()).isEmpty();
  }

  private DocumentEvent createEvent(VirtualFile file) {
    var mock = mock(DocumentEvent.class);
    when(mock.getDocument()).thenReturn(FileDocumentManager.getInstance().getDocument(file));
    return mock;
  }
}
