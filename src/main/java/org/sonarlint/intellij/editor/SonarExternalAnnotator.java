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

import com.intellij.codeInsight.hint.InspectionDescriptionLinkHandler;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.runner.api.Issue;
import org.sonarlint.intellij.issue.IssueStore;

import java.awt.Color;
import java.awt.Font;
import java.util.Collection;

/**
 * When requested, picks up issues from the {@link IssueStore} for a file.
 * Note that external annotators are <b>not</b> called if there are errors reported by annotators, which run before (such as compilation errors).
 *
 */
public class SonarExternalAnnotator extends ExternalAnnotator<SonarExternalAnnotator.AnnotationContext, SonarExternalAnnotator.AnnotationContext> {
  private final boolean unitTest;

  public SonarExternalAnnotator() {
    this(false);
  }

  public SonarExternalAnnotator(boolean unitTest) {
    this.unitTest = unitTest;
  }

  @Override
  public void apply(@NotNull PsiFile file, AnnotationContext annotationResult, @NotNull AnnotationHolder holder) {
    Collection<IssueStore.StoredIssue> issues = annotationResult.store.getForFile(file);
    for (IssueStore.StoredIssue i : issues) {
      // reject non-null ranges that are no longer valid. It probably means that they were deleted from the file.
      if (i.range() == null || i.range().isValid()) {
        addAnnotation(i, holder);
      }
    }
  }

  @Override
  public AnnotationContext collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return collectInformation(file);
  }

  @Override
  public AnnotationContext collectInformation(@NotNull PsiFile file) {
    Project project = file.getProject();
    IssueStore store = project.getComponent(IssueStore.class);
    return new AnnotationContext(store);
  }

  @Override
  @Nullable
  public AnnotationContext doAnnotate(AnnotationContext collectedInfo) {
    return collectedInfo;
  }

  private void addAnnotation(IssueStore.StoredIssue i, AnnotationHolder annotationHolder) {
    Issue issue = i.issue();
    PsiElement el = i.element();
    TextRange textRange;

    // calculate range either from the MarkerRange or from the element itself
    if (i.range() != null) {
      textRange = createTextRange(i.range());
    } else {
      textRange = i.element().getTextRange();
    }

    String msg = getMessage(issue);
    String htmlMsg = getHtmlMessage(issue);

    Annotation annotation = annotationHolder.createAnnotation(getSeverity(issue.getSeverity()), textRange, msg, htmlMsg);
    if (i.range() == null && el instanceof PsiFile) {
      // It is a file issue if it has no range within a file-type element
      annotation.setFileLevelAnnotation(true);
    } else {
      annotation.setEnforcedTextAttributes(getTextAttrs());
    }

    annotation.setGutterIconRenderer(new SonarGutterIconRenderer(issue.getMessage(), issue.getRuleKey(), issue.getRuleKey()));
  }

  private TextAttributes getTextAttrs() {
    Color c;
    if (!unitTest) {
      c = JBColor.YELLOW.darker();
    } else {
      c = Color.yellow;
    }
    return new TextAttributes(null, null, c, EffectType.WAVE_UNDERSCORE, Font.PLAIN);
  }

  /**
   * Check in IntelliJ {@link com.intellij.codeInsight.daemon.impl.LocalInspectionsPass#createHighlightInfo} and
   * {@link InspectionDescriptionLinkHandler}
   */
  private String getHtmlMessage(Issue issue) {
    @NonNls
    final String link = " <a "
      + "href=\"#sonarissue/" + issue.getRuleKey() + "\""
      + (isDark() ? " color=\"7AB4C9\" " : "")
      + ">" + issue.getRuleKey()
      + "</a> ";
    return XmlStringUtil.wrapInHtml(link + XmlStringUtil.escapeString(issue.getMessage()));
  }

  private boolean isDark() {
    // in unit tests on a system without gui (like travis) UIUtil will fail, and Application is not available in simple tests
    return unitTest || ApplicationManager.getApplication().isUnitTestMode() ||
      UIUtil.isUnderDarcula();
  }

  private static String getMessage(Issue issue) {
    return issue.getRuleKey() + " " + issue.getMessage();
  }

  private static HighlightSeverity getSeverity(@Nullable String severity) {
    if (severity == null) {
      return HighlightSeverity.WARNING;
    }
    switch (severity) {
      case "INFO":
        return HighlightSeverity.INFORMATION;
      case "MINOR":
        return HighlightSeverity.WARNING;
      case "BLOCKER":
      case "MAJOR":
      case "CRITICAL":
      default:
        return HighlightSeverity.ERROR;

    }
  }

  private static TextRange createTextRange(RangeMarker rangeMarker) {
    return new TextRange(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
  }

  public static class AnnotationContext {
    IssueStore store;

    AnnotationContext(IssueStore store) {
      this.store = store;
    }
  }
}
