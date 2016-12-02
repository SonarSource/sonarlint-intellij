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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarDocumentListenerTest {
  @Mock
  private Project project;
  @Mock
  private SonarLintSubmitter submitter;
  @Mock
  private EditorFactory editorFactory;
  @Mock
  private SonarLintAppUtils utils;
  @Mock
  private FileDocumentManager docManager;

  private SonarLintGlobalSettings globalSettings;
  private SonarDocumentListener listener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    SonarLintTestUtils.mockMessageBus(project);

    when(editorFactory.getEventMulticaster()).thenReturn(mock(EditorEventMulticaster.class));
    globalSettings = new SonarLintGlobalSettings();
    globalSettings.setAutoTrigger(true);
    listener = new SonarDocumentListener(project, globalSettings, submitter, editorFactory, utils, docManager, 500);
    listener.initComponent();
  }

  @Test
  public void should_trigger() {
    Module m1 = mock(Module.class);
    VirtualFile file = mock(VirtualFile.class);
    Document doc = mock(Document.class);
    DocumentEvent event = mock(DocumentEvent.class);

    when(file.isValid()).thenReturn(true);
    when(event.getDocument()).thenReturn(doc);
    when(docManager.getFile(doc)).thenReturn(file);
    when(utils.guessProjectForFile(file)).thenReturn(project);
    when(utils.findModuleForFile(file, project)).thenReturn(m1);
    when(utils.shouldAnalyzeAutomatically(file, m1)).thenReturn(true);
    when(utils.isOpenFile(project, file)).thenReturn(true);

    listener.documentChanged(event);
    assertThat(listener.getEvents()).hasSize(1);
    verify(submitter, timeout(1000)).submitFiles(Collections.singleton(file), TriggerType.EDITOR_CHANGE, true);
    verifyNoMoreInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_auto_disabled() {
    globalSettings.setAutoTrigger(false);
    Module m1 = mock(Module.class);
    VirtualFile file = mock(VirtualFile.class);
    Document doc = mock(Document.class);
    DocumentEvent event = mock(DocumentEvent.class);

    when(file.isValid()).thenReturn(true);
    when(event.getDocument()).thenReturn(doc);
    when(docManager.getFile(doc)).thenReturn(file);
    when(utils.guessProjectForFile(file)).thenReturn(project);
    when(utils.findModuleForFile(file, project)).thenReturn(m1);
    when(utils.shouldAnalyzeAutomatically(file, m1)).thenReturn(true);

    listener.documentChanged(event);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void dont_trigger_if_check_fails() {
    Module m1 = mock(Module.class);
    VirtualFile file = mock(VirtualFile.class);
    Document doc = mock(Document.class);
    DocumentEvent event = mock(DocumentEvent.class);

    when(file.isValid()).thenReturn(true);
    when(event.getDocument()).thenReturn(doc);
    when(docManager.getFile(doc)).thenReturn(file);
    when(utils.guessProjectForFile(file)).thenReturn(project);
    when(utils.findModuleForFile(file, project)).thenReturn(m1);
    when(utils.shouldAnalyzeAutomatically(file, m1)).thenReturn(false);

    listener.documentChanged(event);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void clear_and_dispose() {
    Module m1 = mock(Module.class);
    VirtualFile file = mock(VirtualFile.class);
    Document doc = mock(Document.class);
    DocumentEvent event = mock(DocumentEvent.class);

    when(file.isValid()).thenReturn(true);
    when(event.getDocument()).thenReturn(doc);
    when(docManager.getFile(doc)).thenReturn(file);
    when(utils.guessProjectForFile(file)).thenReturn(project);
    when(utils.findModuleForFile(file, project)).thenReturn(m1);
    when(utils.shouldAnalyzeAutomatically(file, m1)).thenReturn(true);

    listener.documentChanged(event);
    listener.disposeComponent();
    assertThat(listener.getEvents()).isEmpty();
  }
}
