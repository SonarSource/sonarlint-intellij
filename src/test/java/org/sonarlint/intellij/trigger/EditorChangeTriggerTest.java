/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.assertj.core.api.Assertions.assertThat;
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
    VirtualFile file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    underTest.documentChanged(createEvent(file));

    assertThat(underTest.getEvents()).hasSize(1);
    verify(submitter, timeout(3000)).submitFiles(Collections.singleton(file), TriggerType.EDITOR_CHANGE, true);
    verifyNoMoreInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_auto_disabled() {
    VirtualFile file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");
    getGlobalSettings().setAutoTrigger(false);

    underTest.documentChanged(createEvent(file));
    verifyZeroInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_check_fails() {
    Document doc = mock(Document.class);
    VirtualFile file = createTestFile("Foo.java", Language.findLanguageByID("JAVA"), "public class Foo {}");

    DocumentEvent event = createEvent(file);

    when(event.getDocument()).thenReturn(doc);
    when(docManager.getFile(doc)).thenReturn(file);
    underTest.documentChanged(event);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_project_is_closed() {
    VirtualFile file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    underTest.documentChanged(createEvent(file));

    verifyZeroInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_no_vfile() {
    VirtualFile file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    Document doc = mock(Document.class);
    DocumentEvent event = createEvent(file);

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
    VirtualFile file = createAndOpenTestVirtualFile("MyClass.java", Language.findLanguageByID("JAVA"), "");

    underTest.documentChanged(createEvent(file));
    underTest.dispose();

    assertThat(underTest.getEvents()).isEmpty();
  }

  private DocumentEvent createEvent(VirtualFile file) {
    DocumentEvent mock = mock(DocumentEvent.class);
    when(mock.getDocument()).thenReturn(FileDocumentManager.getInstance().getDocument(file));
    return mock;
  }
}
