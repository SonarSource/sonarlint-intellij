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
package org.sonarlint.intellij.finding.persistence;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindingsCacheTests extends AbstractSonarLintLightTests {
  private FindingsCache underTest;
  private LiveFindingCache<LiveIssue> cache = mock(LiveFindingCache.class);
  private VirtualFile file1 = mock(VirtualFile.class);
  private Document document = mock(Document.class);
  private LiveIssue issue1;

  @BeforeEach
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    when(file1.isValid()).thenReturn(true);
    when(file1.getPath()).thenReturn("file1");

    underTest = new FindingsCache(getProject(), cache);

    issue1 = createRangeStoredIssue(1, "issue 1", 10);

    when(cache.contains(file1)).thenReturn(true);
    when(cache.getLive(file1)).thenReturn(List.of(issue1));
  }

  @Test
  public void should_return_file_issues() {
    assertThat(underTest.getIssuesForFile(file1)).containsExactly(issue1);
  }

  @Test
  public void unknown_file() {
    var unknownFile = mock(VirtualFile.class);
    when(cache.getLive(unknownFile)).thenReturn(null);
    assertThat(underTest.getIssuesForFile(unknownFile)).isEmpty();
  }

  @Test
  public void testClear() {
    underTest.clearAllIssuesForAllFiles();
    verify(cache).clear();
  }

  private LiveIssue createRangeStoredIssue(int id, String rangeContent, int line) {
    var issue = SonarLintTestUtils.createIssue(id);
    when(issue.getTextRange()).thenReturn(new TextRangeDto(line, 1, line, 2));
    var range = mock(RangeMarker.class);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn(rangeContent);
    var file = mock(VirtualFile.class);
    when(file.isValid()).thenReturn(true);
    return new LiveIssue(getModule(), issue, file, range, null, Collections.emptyList());
  }
}
