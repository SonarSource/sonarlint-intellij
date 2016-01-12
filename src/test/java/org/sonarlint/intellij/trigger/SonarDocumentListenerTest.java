/**
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

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.analysis.SonarLintAnalyzer;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;

import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarDocumentListenerTest extends LightPlatformCodeInsightFixtureTestCase {
  private SonarDocumentListener listener;
  private SonarLintAnalyzer analyzer;
  private SonarLintGlobalSettings settings;
  private VirtualFile testFile;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    analyzer = mock(SonarLintAnalyzer.class);
    settings = new SonarLintGlobalSettings();
    settings.setAutoTrigger(true);
    //IntelliJ 14.1 does not have EditorFactory in the container, so we have to mock it
    EditorFactory editorFactory = mock(EditorFactory.class);
    when(editorFactory.getEventMulticaster()).thenReturn(mock(EditorEventMulticaster.class));
    listener = new SonarDocumentListener(super.getProject(), settings, analyzer, editorFactory, 100);
    testFile = myFixture.addFileToProject("test.java", "dummy").getVirtualFile();
  }

  @Test
  public void testAnalyze() throws IOException, InterruptedException {
    DocumentEvent event = createDocEvent();
    listener.documentChanged(event);
    Thread.sleep(500);
    verify(analyzer).submitAsync(any(Module.class), anySetOf(VirtualFile.class));
    verifyNoMoreInteractions(analyzer);
  }

  @Test
  public void testDontAnalyzeWithDisable() throws InterruptedException, IOException {
    settings.setAutoTrigger(false);
    DocumentEvent event = createDocEvent();
    listener.documentChanged(event);
    assertThat(!listener.hasEvents());
    verifyZeroInteractions(analyzer);
  }

  @Test
  public void testDontAnalyzeClosedFile() throws IOException, InterruptedException {
    DocumentEvent event = createDocEvent();
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    editorManager.closeFile(testFile);
    listener.documentChanged(event);
    assertThat(!listener.hasEvents());
    verifyZeroInteractions(analyzer);
  }

  private DocumentEvent createDocEvent() {
    myFixture.openFileInEditor(testFile);
    return new DocumentEventImpl(myFixture.getEditor().getDocument(), 0, "", "my doc", System.currentTimeMillis(), false);
  }

  @After
  public void stop() throws Exception {
    listener.projectClosed();
    listener.disposeComponent();
    super.tearDown();
  }
}
