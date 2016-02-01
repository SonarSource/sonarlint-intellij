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
package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.issue.IssueStore;
import static org.sonarsource.sonarlint.core.IssueListener.Issue;

import java.util.Collection;
import java.util.LinkedList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarExternalAnnotatorTest {
  @Mock
  private PsiFile psiFile;
  @Mock
  private VirtualFile virtualFile;
  @Mock
  private IssueStore store;
  private AnnotationHolderImpl holder;
  private SonarExternalAnnotator.AnnotationContext ctx;
  private TextRange psiFileRange;
  private SonarExternalAnnotator annotator;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    holder = new AnnotationHolderImpl(mock(AnnotationSession.class));
    ctx = new SonarExternalAnnotator.AnnotationContext(store);
    annotator = new SonarExternalAnnotator(true);
    psiFileRange = new TextRange(0, 100);
    when(psiFile.getTextRange()).thenReturn(psiFileRange);
    when(psiFile.getVirtualFile()).thenReturn(virtualFile);
  }

  @Test
  public void testRangeIssues() {
    createStoredIssues(5);
    annotator.apply(psiFile, ctx, holder);

    for (int i = 0; i < 5; i++) {
      assertThat(holder.get(i).getStartOffset()).isEqualTo(i);
      assertThat(holder.get(i).getEndOffset()).isEqualTo(i + 10);
      assertThat(holder.get(i).getMessage()).contains("issue " + i);
      assertThat(holder.get(i).getSeverity()).isEqualTo(HighlightSeverity.WARNING);
      assertThat(holder.get(i).isFileLevelAnnotation()).isFalse();
    }
  }

  @Test
  public void testFileLevelIssues() {
    createFileIssues(5);
    annotator.apply(psiFile, ctx, holder);

    assertThat(holder).hasSize(5);

    for (int i = 0; i < 5; i++) {
      assertThat(holder.get(i).getStartOffset()).isEqualTo(psiFileRange.getStartOffset());
      assertThat(holder.get(i).getEndOffset()).isEqualTo(psiFileRange.getEndOffset());
      assertThat(holder.get(i).isFileLevelAnnotation()).isTrue();
      assertThat(holder.get(i).getMessage()).contains("issue " + i);
      assertThat(holder.get(i).getSeverity()).isEqualTo(HighlightSeverity.WARNING);
    }
  }

  private void createFileIssues(int number) {
    Collection<IssuePointer> issues = new LinkedList<>();

    for (int i = 0; i < number; i++) {
      issues.add(createFileStoredIssue(i, psiFile));
    }

    when(store.getForFile(virtualFile)).thenReturn(issues);
  }

  private void createStoredIssues(int number) {
    Collection<IssuePointer> issues = new LinkedList<>();

    for (int i = 0; i < number; i++) {
      issues.add(createRangeStoredIssue(i, i, i + 10));
    }

    when(store.getForFile(virtualFile)).thenReturn(issues);
  }

  private static IssuePointer createFileStoredIssue(int id, PsiFile file) {
    Issue issue = SonarLintTestUtils.createIssue(id);
    return new IssuePointer(issue, file, null);
  }

  private static IssuePointer createRangeStoredIssue(int id, int rangeStart, int rangeEnd) {
    Issue issue = SonarLintTestUtils.createIssue(id);
    RangeMarker range = mock(RangeMarker.class);

    when(range.getStartOffset()).thenReturn(rangeStart);
    when(range.getEndOffset()).thenReturn(rangeEnd);
    when(range.isValid()).thenReturn(true);

    return new IssuePointer(issue, null, range);
  }
}
