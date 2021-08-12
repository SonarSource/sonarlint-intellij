/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.issue.IssueContext;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.issue.vulnerabilities.TaintVulnerabilitiesPresenter;
import org.sonarlint.intellij.util.SonarLintSeverity;

import static java.util.Collections.emptyList;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isPhpFile;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isPhpLanguageRegistered;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class SonarExternalAnnotator extends ExternalAnnotator<SonarExternalAnnotator.AnnotationContext, SonarExternalAnnotator.AnnotationContext> {

  @Override
  public void apply(@NotNull PsiFile file, AnnotationContext annotationResult, @NotNull AnnotationHolder holder) {
    if (shouldSkip(file)) {
      return;
    }

    Project project = file.getProject();
    IssueManager issueManager = getService(project, IssueManager.class);
    Collection<LiveIssue> issues = issueManager.getForFile(file.getVirtualFile());
    issues.stream()
      .filter(issue -> !issue.isResolved())
      .forEach(issue -> {
        // reject ranges that are no longer valid. It probably means that they were deleted from the file, or the file was deleted
        TextRange validTextRange = getValidTextRange(issue);
        if (validTextRange != null) {
          addAnnotation(project, issue, validTextRange, holder);
        }
      });

    if (SonarLintUtils.enableTaintVulnerabilities()) {
      getService(project, TaintVulnerabilitiesPresenter.class).getCurrentVulnerabilitiesByFile()
        .getOrDefault(file.getVirtualFile(), emptyList())
        .forEach(vulnerability -> addAnnotation(vulnerability, holder));
    }
  }

  @CheckForNull
  private static TextRange getValidTextRange(LiveIssue issue) {
    RangeMarker rangeMarker = issue.getRange();
    if (rangeMarker == null && issue.psiFile().isValid()) {
      return issue.psiFile().getTextRange();
    } else if (rangeMarker != null && rangeMarker.isValid()) {
      return createTextRange(rangeMarker);
    }
    return null;
  }

  private static boolean shouldSkip(@NotNull PsiFile file) {
    // A php file is annotated twice, once by HTML and once by PHP plugin. We want to avoid duplicate annotation
    return isPhpLanguageRegistered() && isPhpFile(file);
  }

  @Override
  public AnnotationContext collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return collectInformation(file);
  }

  @Override
  public AnnotationContext collectInformation(@NotNull PsiFile file) {
    return new AnnotationContext();
  }

  @Override
  @Nullable
  public AnnotationContext doAnnotate(AnnotationContext collectedInfo) {
    return collectedInfo;
  }

  private static void addAnnotation(Project project, LiveIssue issue, TextRange validTextRange, AnnotationHolder annotationHolder) {
    String htmlMsg = getHtmlMessage(issue);

    Annotation annotation = annotationHolder
      .createAnnotation(getSeverity(issue.getSeverity()), validTextRange, issue.getMessage(), htmlMsg);
    annotation.registerFix(new ShowRuleDescriptionIntentionAction(issue.getRuleKey()));
    if (!getSettingsFor(project).isBindingEnabled()) {
      annotation.registerFix(new DisableRuleIntentionAction(issue.getRuleKey()));
    }

    issue.context().ifPresent(c -> annotation.registerFix(new ShowLocationsIntentionAction(issue, c)));
    issue.quickFixes().forEach(f -> annotation.registerFix(new ApplyQuickFixIntentionAction(f, issue.getRuleKey())));

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

  private static void addAnnotation(LocalTaintVulnerability vulnerability, AnnotationHolder annotationHolder) {
    RangeMarker rangeMarker = vulnerability.rangeMarker();
    if (rangeMarker == null) {
      return;
    }
    TextRange textRange = createTextRange(rangeMarker);
    if (textRange.isEmpty()) {
      return;
    }
    String htmlMsg = getHtmlMessage(vulnerability);

    Annotation annotation = annotationHolder
      .createAnnotation(getSeverity(vulnerability.severity()), textRange, vulnerability.message(), htmlMsg);
    annotation.registerFix(new ShowTaintVulnerabilityRuleDescriptionIntentionAction(vulnerability));
    annotation.setTextAttributes(getTextAttrsKey(vulnerability.severity()));

    /*
     * 3 possible ways to set text attributes and error stripe color:
     * - enforce text attributes ({@link Annotation#setEnforcedTextAttributes}) and we need to set everything
     * manually (including error stripe color). This won't be configurable in a standard way and won't change based on used color scheme
     * - rely on one of the default attributes by giving a key {@link com.intellij.openapi.editor.colors.CodeInsightColors} or your own
     * key (SonarLintTextAttributes) to Annotation#setTextAttributes
     * - let Annotation#getTextAttributes decide it based on highlight type and severity.
     */
    annotation.setHighlightType(getType(vulnerability.severity()));
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
  private static String getHtmlMessage(LiveIssue issue) {
    String shortcut = "";
    final KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null && keymapManager.getActiveKeymap() != null) {
      final Keymap keymap = keymapManager.getActiveKeymap();
      shortcut = "(" + KeymapUtil.getShortcutsText(keymap.getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
    }

    @NonNls
    final String link = " <a "
      + "href=\"#sonarissue/" + issue.getRuleKey() + "\""
      + (UIUtil.isUnderDarcula() ? " color=\"7AB4C9\" " : "")
      + ">more...</a> " + shortcut;
    return XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString("SonarLint: " + issue.getMessage()) + issue.context().map(IssueContext::getSummaryDescription).orElse("") + link);
  }

  private static String getHtmlMessage(LocalTaintVulnerability vulnerability) {
    String shortcut = "";
    final KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null) {
      final Keymap keymap = keymapManager.getActiveKeymap();
      shortcut = "(" + KeymapUtil.getShortcutsText(keymap.getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")";
    }

    @NonNls
    final String link = " <a "
      + "href=\"#sonarissue/" + vulnerability.ruleKey() + "\""
      + (UIUtil.isUnderDarcula() ? " color=\"7AB4C9\" " : "")
      + ">more...</a> " + shortcut;
    return XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString("SonarLint: " + vulnerability.message()) + link);
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
  }
}
