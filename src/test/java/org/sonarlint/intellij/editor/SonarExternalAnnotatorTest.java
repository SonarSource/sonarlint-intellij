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
package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarExternalAnnotatorTest extends AbstractSonarLintLightTests {
  private final PsiFile psiFile = mock(PsiFile.class);
  private final VirtualFile virtualFile = mock(VirtualFile.class);
  private final IssueManager store = mock(IssueManager.class);
  private final AnnotationHolderImpl holder = new AnnotationHolderImpl(mock(AnnotationSession.class));
  private final SonarExternalAnnotator.AnnotationContext ctx = new SonarExternalAnnotator.AnnotationContext();
  private final TextRange psiFileRange = new TextRange(0, 100);
  private final SonarExternalAnnotator annotator = new SonarExternalAnnotator();
  private final Document document = mock(Document.class);

  @Before
  public void set() {
    replaceProjectService(IssueManager.class, store);
    when(psiFile.getTextRange()).thenReturn(psiFileRange);
    when(psiFile.getVirtualFile()).thenReturn(virtualFile);
    when(psiFile.getFileType()).thenReturn(JavaFileType.INSTANCE);
    when(psiFile.getProject()).thenReturn(getProject());
  }

  @Test
  public void testRangeIssues() {
    createStoredIssues(5);

    holder.applyExternalAnnotatorWithContext(psiFile, annotator, ctx);

    for (int i = 0; i < 5; i++) {
      assertThat(holder.get(i).getStartOffset()).isEqualTo(i);
      assertThat(holder.get(i).getEndOffset()).isEqualTo(i + 10);
      assertThat(holder.get(i).getMessage()).contains("issue " + i);
      assertThat(holder.get(i).getSeverity()).isEqualTo(HighlightSeverity.WARNING);
      assertThat(holder.get(i).isFileLevelAnnotation()).isFalse();
    }
  }

  @Test
  public void testSeverityMapping() {
    assertThat(SonarExternalAnnotator.getTextAttrsKey(null)).isEqualTo(SonarLintTextAttributes.MAJOR);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(IssueSeverity.MAJOR)).isEqualTo(SonarLintTextAttributes.MAJOR);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(IssueSeverity.MINOR)).isEqualTo(SonarLintTextAttributes.MINOR);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(IssueSeverity.BLOCKER)).isEqualTo(SonarLintTextAttributes.BLOCKER);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(IssueSeverity.CRITICAL)).isEqualTo(SonarLintTextAttributes.CRITICAL);
    assertThat(SonarExternalAnnotator.getTextAttrsKey(IssueSeverity.INFO)).isEqualTo(SonarLintTextAttributes.INFO);
  }

  @Test
  public void testFileLevelIssues() {
    when(psiFile.isValid()).thenReturn(true);
    createFileIssues(5);

    holder.applyExternalAnnotatorWithContext(psiFile, annotator, ctx);

    assertThat(holder).hasSize(5);

    for (int i = 0; i < 5; i++) {
      assertThat(holder.get(i).getStartOffset()).isEqualTo(psiFileRange.getStartOffset());
      assertThat(holder.get(i).getEndOffset()).isEqualTo(psiFileRange.getEndOffset());
      assertThat(holder.get(i).isFileLevelAnnotation()).isTrue();
      assertThat(holder.get(i).getMessage()).contains("issue " + i);
      assertThat(holder.get(i).getSeverity()).isEqualTo(HighlightSeverity.WARNING);
    }
  }

  @Test
  public void should_not_annotate_issue_with_invalid_text_range_offset() {
    when(psiFile.isValid()).thenReturn(true);
    var range = mock(RangeMarker.class);
    when(range.getStartOffset()).thenReturn(1);
    when(range.getEndOffset()).thenReturn(0);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn("foo");
    var issueWithWrongTextRange = mock(LiveIssue.class);
    when(issueWithWrongTextRange.getRange()).thenReturn(range);
    when(store.getForFile(virtualFile)).thenReturn(List.of(issueWithWrongTextRange));

    holder.applyExternalAnnotatorWithContext(psiFile, annotator, ctx);

    assertThat(holder).isEmpty();
  }

  @Test
  public void should_not_annotate_issue_with_overlapping_text_range() {
    when(psiFile.isValid()).thenReturn(true);
    when(psiFile.getTextRange()).thenReturn(new TextRange(0, 5));
    var range = mock(RangeMarker.class);
    when(range.getStartOffset()).thenReturn(2);
    when(range.getEndOffset()).thenReturn(6);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn("foo");
    var issueWithWrongTextRange = mock(LiveIssue.class);
    when(issueWithWrongTextRange.getRange()).thenReturn(range);
    when(store.getForFile(virtualFile)).thenReturn(List.of(issueWithWrongTextRange));

    holder.applyExternalAnnotatorWithContext(psiFile, annotator, ctx);

    assertThat(holder).isEmpty();
  }

  private void createFileIssues(int number) {
    Collection<LiveIssue> issues = new LinkedList<>();

    for (int i = 0; i < number; i++) {
      issues.add(createFileStoredIssue(i, psiFile));
    }

    when(store.getForFile(virtualFile)).thenReturn(issues);
  }

  private void createStoredIssues(int number) {
    Collection<LiveIssue> issues = new LinkedList<>();

    for (int i = 0; i < number; i++) {
      issues.add(createRangeStoredIssue(i, i, i + 10, "foo " + i));
    }

    when(store.getForFile(virtualFile)).thenReturn(issues);
  }

  private static LiveIssue createFileStoredIssue(int id, PsiFile file) {
    var issue = SonarLintTestUtils.createIssue(id);
    return new LiveIssue(issue, file, null, null, Collections.emptyList());
  }

  private LiveIssue createRangeStoredIssue(int id, int rangeStart, int rangeEnd, String text) {
    var issue = SonarLintTestUtils.createIssue(id);
    var range = mock(RangeMarker.class);

    when(range.getStartOffset()).thenReturn(rangeStart);
    when(range.getEndOffset()).thenReturn(rangeEnd);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn(text);
    var psiFile = mock(PsiFile.class);
    when(psiFile.isValid()).thenReturn(true);
    when(psiFile.getTextRange()).thenReturn(new TextRange(rangeStart, rangeEnd));
    return new LiveIssue(issue, psiFile, range, null, Collections.emptyList());
  }
}
