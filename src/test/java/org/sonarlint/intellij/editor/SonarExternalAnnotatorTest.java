/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarExternalAnnotatorTest extends SonarTest {
  private PsiFile psiFile = mock(PsiFile.class);
  private VirtualFile virtualFile = mock(VirtualFile.class);
  private IssueManager store = mock(IssueManager.class);
  private AnnotationHolderImpl holder = new AnnotationHolderImpl(mock(AnnotationSession.class));
  private SonarExternalAnnotator.AnnotationContext ctx = new SonarExternalAnnotator.AnnotationContext(store);
  private TextRange psiFileRange = new TextRange(0, 100);
  private SonarExternalAnnotator annotator = new SonarExternalAnnotator(true);
  private Document document = mock(Document.class);
  private SonarLintGlobalSettings globalSettings = new SonarLintGlobalSettings();
  private SonarLintProjectSettings projectSettings = new SonarLintProjectSettings();

  @Before
  public void set() {
    super.register(app, SonarLintGlobalSettings.class, globalSettings);
    super.register(SonarLintProjectSettings.class, projectSettings);
    when(psiFile.getTextRange()).thenReturn(psiFileRange);
    when(psiFile.getVirtualFile()).thenReturn(virtualFile);
    when(psiFile.getFileType()).thenReturn(JavaFileType.INSTANCE);
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
  public void testSeverityMapping() {
    assertThat(SonarExternalAnnotator.getTextAttrsKey(null)).isEqualTo(SonarLintTextAttributes.MAJOR);
    assertThat(SonarExternalAnnotator.getTextAttrsKey("MAJOR")).isEqualTo(SonarLintTextAttributes.MAJOR);
    assertThat(SonarExternalAnnotator.getTextAttrsKey("MINOR")).isEqualTo(SonarLintTextAttributes.MINOR);
    assertThat(SonarExternalAnnotator.getTextAttrsKey("BLOCKER")).isEqualTo(SonarLintTextAttributes.BLOCKER);
    assertThat(SonarExternalAnnotator.getTextAttrsKey("CRITICAL")).isEqualTo(SonarLintTextAttributes.CRITICAL);
    assertThat(SonarExternalAnnotator.getTextAttrsKey("INFO")).isEqualTo(SonarLintTextAttributes.INFO);
    assertThat(SonarExternalAnnotator.getTextAttrsKey("unknown")).isEqualTo(SonarLintTextAttributes.MAJOR);
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
    Issue issue = SonarLintTestUtils.createIssue(id);
    return new LiveIssue(issue, file, null, Collections.emptyList());
  }

  private LiveIssue createRangeStoredIssue(int id, int rangeStart, int rangeEnd, String text) {
    Issue issue = SonarLintTestUtils.createIssue(id);
    RangeMarker range = mock(RangeMarker.class);

    when(range.getStartOffset()).thenReturn(rangeStart);
    when(range.getEndOffset()).thenReturn(rangeEnd);
    when(range.isValid()).thenReturn(true);
    when(range.getDocument()).thenReturn(document);
    when(document.getText(any(TextRange.class))).thenReturn(text);
    return new LiveIssue(issue, mock(PsiFile.class), range, Collections.emptyList());
  }
}
