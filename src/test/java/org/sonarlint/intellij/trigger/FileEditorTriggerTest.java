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

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.analysis.SonarLintAnalyzer;
import org.sonarlint.intellij.issue.IssueStore;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class FileEditorTriggerTest extends LightPlatformCodeInsightFixtureTestCase {
  private FileEditorTrigger trigger;
  private IssueStore store;
  private SonarLintAnalyzer analyzer;
  private VirtualFile testFile;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    store = mock(IssueStore.class);
    analyzer = mock(SonarLintAnalyzer.class);
    trigger = new FileEditorTrigger(getProject(), store, analyzer);
    testFile = myFixture.addFileToProject("testFile.java", "dummy").getVirtualFile();
  }

  @Test
  public void testOpenFile() {
    trigger.fileOpened(getProject().getComponent(FileEditorManager.class), testFile);
    verify(analyzer).submitAsync(myModule, Collections.singleton(testFile));
    verifyNoMoreInteractions(analyzer);
    verifyZeroInteractions(store);
  }

  @Test
  public void testCloseFile() {
    trigger.fileClosed(getProject().getComponent(FileEditorManager.class), testFile);
    verify(store).clean(testFile);
    verifyNoMoreInteractions(store);
    verifyZeroInteractions(analyzer);
  }
}
