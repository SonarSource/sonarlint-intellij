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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.actions.NoSonarIntentionAction;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.issue.IssueStore;
import org.sonarlint.intellij.util.SonarLintSeverity;
import static org.sonarsource.sonarlint.core.IssueListener.Issue;

import java.util.Collection;

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
    Collection<IssuePointer> issues = annotationResult.store.getForFile(file.getVirtualFile());
    for (IssuePointer i : issues) {
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

  private void addAnnotation(IssuePointer i, AnnotationHolder annotationHolder) {
    Issue issue = i.issue();
    TextRange textRange;

    if (i.range() != null) {
      textRange = createTextRange(i.range());
    } else {
      textRange = i.psiFile().getTextRange();
    }

    String msg = getMessage(issue);
    String htmlMsg = getHtmlMessage(issue);

    Annotation annotation = annotationHolder.createAnnotation(getSeverity(issue.getSeverity()), textRange, msg, htmlMsg);

    if (i.range() == null) {
      annotation.setFileLevelAnnotation(true);
    } else {
      annotation.setTextAttributes(getTextAttrsKey(issue.getSeverity()));
    }

    /**
     * 3 possible ways to set text attributes and error stripe color:
     * - enforce text attributes ({@link Annotation#setEnforcedTextAttributes}) and we need to set everything manually
     * (including error stripe color). This won't be configurable in a standard way and won't change based on used color scheme;
     * - rely on one of the default attributes by giving a key {@link com.intellij.openapi.editor.colors.CodeInsightColors} or your own
     * key ({@link SonarLintTextAttributes} to {@link Annotation#setTextAttributes}
     * - let {@link Annotation#getTextAttributes} decide it based on highlight type and severity.
     */
    annotation.setHighlightType(getType(issue.getSeverity()));
    if(i.range() != null) {
      annotation.registerFix(new NoSonarIntentionAction(i.range()));
    }
  }

  private static TextAttributesKey getTextAttrsKey(@Nullable String severity) {
    if(severity == null) {
      return SonarLintTextAttributes.MAJOR;
    }
    switch (severity) {
      case "MINOR":
        return SonarLintTextAttributes.MINOR;
      case "BLOCKER":
        return SonarLintTextAttributes.BLOCKER;
      case "INFO":
        return SonarLintTextAttributes.INFO;
      case "CRITICAL":
        return SonarLintTextAttributes.CRITICAL;
      case "MAJOR":
      default:
        return SonarLintTextAttributes.MAJOR;
    }
  }

  /**
   * Check in IntelliJ {@link com.intellij.codeInsight.daemon.impl.LocalInspectionsPass#createHighlightInfo} and
   * {@link InspectionDescriptionLinkHandler}
   * {@link com.intellij.openapi.editor.colors.CodeInsightColors}
   */
  private String getHtmlMessage(Issue issue) {
    @NonNls
    final String link = " <a "
      + "href=\"#sonarissue/" + issue.getRuleKey() + "\""
      + (isDark() ? " color=\"7AB4C9\" " : "")
      + ">more...</a> ";
    return XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(issue.getMessage()) + link);
  }

  private boolean isDark() {
    // in unit tests on a system without gui (like travis) UIUtil will fail, and Application is not available in simple tests
    return unitTest || ApplicationManager.getApplication().isUnitTestMode() ||
      UIUtil.isUnderDarcula();
  }

  private static String getMessage(Issue issue) {
    return issue.getMessage();
  }

  /**
   * Must be consistent with {@link #getSeverity}.
   * @see Annotation#getTextAttributes
   */
  private static ProblemHighlightType getType(@Nullable String severity) {
    if (severity == null) {
      return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }

    return SonarLintSeverity.byName(severity).highlightType();
  }

  /**
   * Must be consistent with {@link #getType}.
   * @see Annotation#getTextAttributes
   */
  private static HighlightSeverity getSeverity(@Nullable String severity) {
    if (severity == null) {
      return HighlightSeverity.WARNING;
    }

    return SonarLintSeverity.byName(severity).highlightSeverity();
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
