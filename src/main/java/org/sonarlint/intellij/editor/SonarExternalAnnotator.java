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

import com.intellij.codeInsight.hint.InspectionDescriptionLinkHandler;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import java.util.Collection;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintSeverity;
import org.sonarlint.intellij.util.SonarLintUtils;

import static org.sonarlint.intellij.util.SonarLintUtils.isPhpFileInPhpStorm;

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
    // In PHPStorm the same PHP file is analyzed twice (once as PHP file and once as HTML file)
    if (isPhpFileInPhpStorm(file.getFileType())) {
      return;
    }

    Collection<LiveIssue> issues = annotationResult.store.getForFile(file.getVirtualFile());
    issues.stream()
      .filter(issue -> !issue.isResolved())
      .forEach(issue -> {
        // reject non-null ranges that are no longer valid. It probably means that they were deleted from the file.
        RangeMarker range = issue.getRange();
        if (range == null || range.isValid()) {
          addAnnotation(issue, holder);
        }
      });
  }

  @Override
  public AnnotationContext collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return collectInformation(file);
  }

  @Override
  public AnnotationContext collectInformation(@NotNull PsiFile file) {
    Project project = file.getProject();
    IssueManager store = SonarLintUtils.get(project, IssueManager.class);
    return new AnnotationContext(store);
  }

  @Override
  @Nullable
  public AnnotationContext doAnnotate(AnnotationContext collectedInfo) {
    return collectedInfo;
  }

  private void addAnnotation(LiveIssue issue, AnnotationHolder annotationHolder) {
    TextRange textRange;

    if (issue.getRange() != null) {
      textRange = createTextRange(issue.getRange());
    } else {
      textRange = issue.psiFile().getTextRange();
    }

    String htmlMsg = getHtmlMessage(issue);

    Annotation annotation = annotationHolder
      .createAnnotation(getSeverity(issue.getSeverity()), textRange, issue.getMessage(), htmlMsg);
    annotation.registerFix(new DisableRuleQuickFix(issue.getRuleKey()));

    if (!issue.flows().isEmpty()) {
      annotation.registerFix(new ShowLocationsIntention(issue.getRange(), issue.getMessage(), issue.flows()));
    }

    if (issue.getRange() == null) {
      annotation.setFileLevelAnnotation(true);
    } else {
      annotation.setTextAttributes(getTextAttrsKey(issue.getSeverity()));
    }

    /*
     * 3 possible ways to set text attributes and error stripe color:
     * - enforce text attributes ({@link Annotation#setEnforcedTextAttributes}) and we need to set everything
     * manually (including error stripe color). This won't be configurable in a standard way and won't change based on used color scheme
     * - rely on one of the default attributes by giving a key {@link com.intellij.openapi.editor.colors.CodeInsightColors} or your own
     * key (SonarLintTextAttributes) to Annotation#setTextAttributes
     * - let Annotation#getTextAttributes decide it based on highlight type and severity.
     */
    annotation.setHighlightType(getType(issue.getSeverity()));
  }

  static TextAttributesKey getTextAttrsKey(@Nullable String severity) {
    if (severity == null) {
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
   * {@link com.intellij.codeInsight.daemon.impl.LocalInspectionsPass}
   * {@link com.intellij.openapi.editor.colors.CodeInsightColors}
   */
  private String getHtmlMessage(LiveIssue issue) {
    String shortcut = "";
    final KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null && keymapManager.getActiveKeymap() != null) {
      final Keymap keymap = keymapManager.getActiveKeymap();
      shortcut = "(" + KeymapUtil.getShortcutsText(keymap.getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
    }

    String flows = "";
    if (!issue.flows().isEmpty()) {
      int numLocations = issue.flows().stream().mapToInt(f -> f.locations().size()).sum();
      flows = String.format(" [+%d %s]", numLocations, SonarLintUtils.pluralize("location", numLocations));
    }

    @NonNls final String link = " <a "
      + "href=\"#sonarissue/" + issue.getRuleKey() + "\""
      + (isDark() ? " color=\"7AB4C9\" " : "")
      + ">more...</a> " + shortcut;
    return XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString("SonarLint: " + issue.getMessage()) + flows + link);
  }

  private boolean isDark() {
    // in unit tests on a system without gui (like travis) UIUtil will fail, and Application is not available in simple tests
    return unitTest || ApplicationManager.getApplication().isUnitTestMode() ||
      UIUtil.isUnderDarcula();
  }

  /**
   * Must be consistent with {@link #getSeverity}.
   *
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
   *
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
    final IssueManager store;

    AnnotationContext(IssueManager store) {
      this.store = store;
    }
  }
}
