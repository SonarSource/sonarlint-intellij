/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.editor;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CodeAnalyzerRestarterTests extends AbstractSonarLintLightTests {

  private final DirectHighlighter directHighlighter = mock(DirectHighlighter.class);
  private CodeAnalyzerRestarter analyzerRestarter;

  @BeforeEach
  void prepare() {
    analyzerRestarter = new CodeAnalyzerRestarter(getProject(), directHighlighter);
  }

  @Test
  void should_not_highlight_when_no_files() {
    analyzerRestarter.refreshFiles(List.of());

    verifyNoInteractions(directHighlighter);
  }

  @Test
  void should_highlight_all_open_files() {
    var file1 = createAndOpenTestPsiFile("Foo.java", "class Foo {}").getVirtualFile();
    var file2 = createAndOpenTestPsiFile("Bar.java", "class Bar {}").getVirtualFile();

    analyzerRestarter.refreshOpenFiles();

    verify(directHighlighter, timeout(1000)).updateHighlights(argThat(files -> files.containsAll(Set.of(file1, file2))));
    verifyNoMoreInteractions(directHighlighter);
  }

  @Test
  void should_highlight_requested_files() {
    var file1 = createAndOpenTestPsiFile("Foo.java", "class Foo {}").getVirtualFile();
    var file2 = createTestPsiFile("Bar.java", "class Bar {}").getVirtualFile();

    analyzerRestarter.refreshFiles(List.of(file1, file2));

    verify(directHighlighter, timeout(1000)).updateHighlights(Set.of(file1, file2));
    verifyNoMoreInteractions(directHighlighter);
  }

  @Test
  void should_debounce_rapid_calls_into_single_pass() {
    var file1 = createAndOpenTestPsiFile("Foo.java", "class Foo {}").getVirtualFile();
    var file2 = createTestPsiFile("Bar.java", "class Bar {}").getVirtualFile();

    analyzerRestarter.refreshFiles(List.of(file1));
    analyzerRestarter.refreshFiles(List.of(file2));

    verify(directHighlighter, timeout(1000)).updateHighlights(Set.of(file1, file2));
    verifyNoMoreInteractions(directHighlighter);
  }

}
