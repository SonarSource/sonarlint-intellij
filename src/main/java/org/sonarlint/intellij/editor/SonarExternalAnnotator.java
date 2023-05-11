/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesPresenter;
import org.sonarlint.intellij.finding.persistence.FindingsCache;
import org.sonarlint.intellij.util.SonarLintSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;

import static java.util.Collections.emptyList;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isPhpFile;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isPhpLanguageRegistered;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class SonarExternalAnnotator extends ExternalAnnotator<SonarExternalAnnotator.AnnotationContext, SonarExternalAnnotator.AnnotationContext> {

  // some quick fixes do not match the IntelliJ experience
  private static final Set<String> SILENCED_QUICK_FIXABLE_RULE_KEYS = Set.of("java:S1068", "java:S1144", "java:S1172");

  @Override
  public void apply(@NotNull PsiFile file, AnnotationContext annotationResult, @NotNull AnnotationHolder holder) {
    if (shouldSkip(file)) {
      return;
    }

    var project = file.getProject();
    var issueManager = getService(project, FindingsCache.class);
    var issues = issueManager.getIssuesForFile(file.getVirtualFile());
    issues.stream()
      .filter(issue -> !issue.isResolved())
      .forEach(issue -> {
        // reject ranges that are no longer valid. It probably means that they were deleted from the file, or the file was deleted
        var validTextRange = issue.getValidTextRange();
        if (validTextRange != null) {
          addAnnotation(project, issue, validTextRange, holder);
        }
      });

    var toolWindowService = getService(project, SonarLintToolWindow.class);
    if (toolWindowService.isSecurityHotspotsTabActive()) {
      var securityHotspots = issueManager.getSecurityHotspotsForFile(file.getVirtualFile());
      securityHotspots.stream()
        .filter(securityHotspot -> !securityHotspot.isResolved())
        .forEach(securityHotspot -> {
          // reject ranges that are no longer valid. It probably means that they were deleted from the file, or the file was deleted
          var validTextRange = securityHotspot.getValidTextRange();
          if (validTextRange != null) {
            addAnnotation(project, securityHotspot, validTextRange, holder);
          }
        });
    }

    if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
      getService(project, TaintVulnerabilitiesPresenter.class).getCurrentVulnerabilitiesByFile()
        .getOrDefault(file.getVirtualFile(), emptyList())
        .forEach(vulnerability -> addAnnotation(vulnerability, holder));
    }
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

  private static void addAnnotation(Project project, LiveFinding finding, TextRange validTextRange, AnnotationHolder annotationHolder) {
    var intentionActions = new ArrayList<IntentionAction>();
    intentionActions.add(new ShowRuleDescriptionIntentionAction(finding.getRuleKey(), finding.uid()));
    if (!getSettingsFor(project).isBindingEnabled()) {
      intentionActions.add(new DisableRuleIntentionAction(finding.getRuleKey()));
    }

    if (shouldSuggestQuickFix(finding)) {
      finding.quickFixes().forEach(f -> intentionActions.add(new ApplyQuickFixIntentionAction(f, finding.getRuleKey())));
    }

    if (finding instanceof LiveSecurityHotspot) {
      intentionActions.add(new ReviewSecurityHotspotAction(finding.getServerFindingKey()));
    }

    finding.context().ifPresent(c -> intentionActions.add(new ShowLocationsIntentionAction(finding, c)));

    var annotationBuilder = annotationHolder
      .newAnnotation(getSeverity(finding.getUserSeverity()), finding.getMessage())
      .range(validTextRange);
    for (IntentionAction action : intentionActions) {
      annotationBuilder = annotationBuilder.withFix(action);
    }

    if (finding.getRange() == null) {
      annotationBuilder = annotationBuilder.fileLevel();
    } else {
      annotationBuilder = annotationBuilder.textAttributes(getTextAttrsKey(finding.getUserSeverity()));
    }

    annotationBuilder.highlightType(getType(finding.getUserSeverity()))
      .create();
  }

  private static boolean shouldSuggestQuickFix(LiveFinding issue) {
    return !SILENCED_QUICK_FIXABLE_RULE_KEYS.contains(issue.getRuleKey());
  }

  private static void addAnnotation(LocalTaintVulnerability vulnerability, AnnotationHolder annotationHolder) {
    var textRange = vulnerability.getValidTextRange();
    if (textRange == null) {
      return;
    }
    annotationHolder.newAnnotation(getSeverity(vulnerability.severity()), vulnerability.message())
      .range(textRange)
      .withFix(new ShowTaintVulnerabilityRuleDescriptionIntentionAction(vulnerability))
      .textAttributes(getTextAttrsKey(vulnerability.severity()))
      .highlightType(getType(vulnerability.severity()))
      .create();
  }

  static TextAttributesKey getTextAttrsKey(@Nullable IssueSeverity severity) {
    if (severity == null) {
      return SonarLintTextAttributes.MAJOR;
    }
    switch (severity) {
      case MINOR:
        return SonarLintTextAttributes.MINOR;
      case BLOCKER:
        return SonarLintTextAttributes.BLOCKER;
      case INFO:
        return SonarLintTextAttributes.INFO;
      case CRITICAL:
        return SonarLintTextAttributes.CRITICAL;
      case MAJOR:
      default:
        return SonarLintTextAttributes.MAJOR;
    }
  }

  /**
   * Must be consistent with {@link #getSeverity}.
   *
   * @see Annotation#getTextAttributes
   */
  private static ProblemHighlightType getType(@Nullable IssueSeverity severity) {
    if (severity == null) {
      return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }

    return SonarLintSeverity.fromCoreSeverity(severity).highlightType();
  }

  /**
   * Must be consistent with {@link #getType}.
   *
   * @see Annotation#getTextAttributes
   */
  private static HighlightSeverity getSeverity(@Nullable IssueSeverity severity) {
    if (severity == null) {
      return HighlightSeverity.WARNING;
    }

    return SonarLintSeverity.fromCoreSeverity(severity).highlightSeverity();
  }

  public static class AnnotationContext {
  }
}
