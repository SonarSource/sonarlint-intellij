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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.analysis.SonarLintAnalyzer;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.issue.IssueProcessor;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
  private EditorFactory editorFactory;
  private SonarLintGlobalSettings settings;
  private VirtualFile testFile;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    settings = new SonarLintGlobalSettings();
    settings.setAutoTrigger(true);
    //IntelliJ 14.1 does not have EditorFactory in the container, so we have to mock it
    editorFactory = mock(EditorFactory.class);
    when(editorFactory.getEventMulticaster()).thenReturn(mock(EditorEventMulticaster.class));
    testFile = myFixture.addFileToProject("test.java", "dummy").getVirtualFile();
  }

  @Test
  public void testAnalyze() throws IOException, InterruptedException, TimeoutException, BrokenBarrierException {
    TestAnalyzer analyzer = new TestAnalyzer(getProject());
    listener = new SonarDocumentListener(super.getProject(), settings, analyzer, editorFactory, 100);
    listener.initComponent();

    DocumentEvent event = createDocEvent();
    listener.documentChanged(event);
    analyzer.waitForSubmission(500);
  }

  @Test
  public void testDontAnalyzeWithDisable() throws InterruptedException, IOException {
    SonarLintAnalyzer analyzer = mock(SonarLintAnalyzer.class);
    listener = new SonarDocumentListener(super.getProject(), settings, analyzer, editorFactory, 100);
    listener.initComponent();

    settings.setAutoTrigger(false);
    DocumentEvent event = createDocEvent();
    listener.documentChanged(event);
    assertThat(listener.hasEvents()).isFalse();
    verifyZeroInteractions(analyzer);
  }

  private DocumentEvent createDocEvent() {
    myFixture.openFileInEditor(testFile);
    return new DocumentEventImpl(myFixture.getEditor().getDocument(), 0, "", "my doc", System.currentTimeMillis(), false);
  }

  @After
  public void tearDown() throws Exception {
    listener.projectClosed();
    listener.disposeComponent();
    super.tearDown();
  }

  private class TestAnalyzer extends SonarLintAnalyzer {
    private CyclicBarrier barrier;

    public TestAnalyzer(Project project) {
      super(project, null);
      barrier = new CyclicBarrier(2);
    }

    public void waitForSubmission(int maxTime) throws InterruptedException, TimeoutException, BrokenBarrierException {
      barrier.await(maxTime, TimeUnit.MILLISECONDS);
    }

    @Override
    public void submitAsync(Module m, Set<VirtualFile> files) {
      try {
        barrier.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (BrokenBarrierException e) {
        e.printStackTrace();
      }
    }
  }
}
