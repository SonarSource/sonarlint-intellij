/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import org.sonarlint.intellij.actions.MarkAsResolvedAction;
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.actions.SuggestCodeFixIntentionAction;
import org.sonarlint.intellij.cayc.CleanAsYouCodeService;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.util.SonarLintSeverity;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isPhpFile;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isPhpLanguageRegistered;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class SonarExternalAnnotator extends ExternalAnnotator<SonarExternalAnnotator.AnnotationContext, SonarExternalAnnotator.AnnotationContext> {

  // some quick fixes do not match the IntelliJ experience
  private static final Set<String> SILENCED_QUICK_FIXABLE_RULE_KEYS = Set.of("java:S1068", "java:S1144", "java:S1172");

  @Override
  public void apply(PsiFile psiFile, AnnotationContext annotationResult, AnnotationHolder holder) {
    if (shouldSkip(psiFile)) {
      return;
    }

    var project = psiFile.getProject();
    var isFocusOnNewCode = getService(CleanAsYouCodeService.class).shouldFocusOnNewCode();

    var toolWindowService = getService(project, SonarLintToolWindow.class);
    var fileTextRange = psiFile.getTextRange();
    toolWindowService.getDisplayedFindings().getIssues().stream()
      .filter(issue -> !issue.isResolved() && (!isFocusOnNewCode || issue.isOnNewCode()))
      .forEach(issue -> {
        var validTextRange = issue.getValidTextRange();
        if (validTextRange != null && fileTextRange.contains(validTextRange)) {
          addAnnotation(project, issue, validTextRange, holder);
        }
      });

    toolWindowService.getDisplayedFindings().getHotspots().stream()
      .filter(securityHotspot -> !securityHotspot.isResolved() && (!isFocusOnNewCode || securityHotspot.isOnNewCode()))
      .forEach(securityHotspot -> {
        var validTextRange = securityHotspot.getValidTextRange();
        if (validTextRange != null && fileTextRange.contains(validTextRange)) {
          addAnnotation(project, securityHotspot, validTextRange, holder);
        }
      });

    var currentFile = psiFile.getVirtualFile();
    toolWindowService.getDisplayedFindings().getTaints().stream()
      .filter(vulnerability -> !vulnerability.isResolved() && (!isFocusOnNewCode || vulnerability.isOnNewCode()))
      .filter(vulnerability -> currentFile.equals(vulnerability.file()))
      .forEach(vulnerability -> addAnnotation(vulnerability, fileTextRange, holder));
  }

  private static boolean shouldSkip(PsiFile file) {
    // A php file is annotated twice, once by HTML and once by PHP plugin. We want to avoid duplicate annotation
    return isPhpLanguageRegistered() && isPhpFile(file);
  }

  @Override
  public AnnotationContext collectInformation(PsiFile file, Editor editor, boolean hasErrors) {
    return collectInformation(file);
  }

  @Override
  public AnnotationContext collectInformation(PsiFile file) {
    return new AnnotationContext();
  }

  @Override
  @Nullable
  public AnnotationContext doAnnotate(AnnotationContext collectedInfo) {
    return collectedInfo;
  }

  private static void addAnnotation(Project project, LiveFinding finding, TextRange validTextRange, AnnotationHolder annotationHolder) {
    var intentionActions = new ArrayList<IntentionAction>();
    intentionActions.add(new ShowRuleDescriptionIntentionAction(finding));
    if (!getSettingsFor(project).isBindingEnabled()) {
      intentionActions.add(new DisableRuleIntentionAction(finding.getRuleKey()));
    }

    if (shouldSuggestQuickFix(finding)) {
      finding.quickFixes().forEach(fix -> {
        // Actions are only supported on quick fixes not spanning multiple files! If this is going to change at some
        // point in the future to support cross-file spanning quick fix actions, then
        // "ApplyQuickFixIntentionAction.invoke(...)" has to be changed by moving the guard into the method. This is due
        // IntelliJ is currently only supporting the preview of quick fixes on ONE file at the time!
        if (fix.isSingleFile()) {
          intentionActions.add(
            new ApplyQuickFixIntentionAction(fix, finding.getRuleKey(), false));
        }
      });
    }

    if (finding instanceof LiveSecurityHotspot hotspot) {
      intentionActions.add(new ReviewSecurityHotspotAction(finding.getServerKey(), hotspot.getStatus()));
    }

    if (finding instanceof LiveIssue liveIssue) {
      intentionActions.add(new MarkAsResolvedAction(liveIssue));
      if (liveIssue.isAiCodeFixable()) {
        intentionActions.add(new SuggestCodeFixIntentionAction(liveIssue));
      }
    }

    finding.context().ifPresent(c -> intentionActions.add(new ShowLocationsIntentionAction(finding, c)));

    var annotationBuilder = annotationHolder
      .newAnnotation(getSeverity(finding.getHighestImpact(), finding.getUserSeverity()), finding.getMessage())
      .range(validTextRange);
    for (IntentionAction action : intentionActions) {
      annotationBuilder = annotationBuilder.withFix(action);
    }

    if (finding.getRange() == null) {
      annotationBuilder = annotationBuilder.fileLevel();
    } else {
      annotationBuilder = annotationBuilder.textAttributes(getTextAttrsKey(finding.getHighestImpact(), finding.getUserSeverity(), finding.isOnNewCode()));
    }

    annotationBuilder.highlightType(getType(finding.getHighestImpact(), finding.getUserSeverity()))
      .create();
  }

  private static boolean shouldSuggestQuickFix(LiveFinding issue) {
    return !SILENCED_QUICK_FIXABLE_RULE_KEYS.contains(issue.getRuleKey());
  }

  private static void addAnnotation(LocalTaintVulnerability vulnerability, TextRange fileTextRange, AnnotationHolder annotationHolder) {
    var textRange = vulnerability.getValidTextRange();
    if (textRange == null || !vulnerability.isValid() || !fileTextRange.contains(textRange)) {
      return;
    }

    annotationHolder.newAnnotation(getSeverity(vulnerability.getHighestImpact(), vulnerability.severity()), vulnerability.message())
      .range(textRange)
      .withFix(new ShowTaintVulnerabilityRuleDescriptionIntentionAction(vulnerability))
      .withFix(new MarkAsResolvedAction(vulnerability))
      .textAttributes(getTextAttrsKey(vulnerability.getHighestImpact(), vulnerability.severity(), vulnerability.isOnNewCode()))
      .highlightType(getType(vulnerability.getHighestImpact(), vulnerability.severity()))
      .create();
  }

  static TextAttributesKey getTextAttrsKey(@Nullable ImpactSeverity impact, @Nullable IssueSeverity severity, boolean isOnNewCode) {
    if (getService(CleanAsYouCodeService.class).shouldFocusOnNewCode() && !isOnNewCode) {
      return SonarLintTextAttributes.OLD_CODE;
    }

    if (impact != null) {
      return switch (impact) {
        case BLOCKER -> SonarLintTextAttributes.BLOCKER;
        case HIGH -> SonarLintTextAttributes.HIGH;
        case LOW -> SonarLintTextAttributes.LOW;
        case INFO -> SonarLintTextAttributes.INFO;
        default -> SonarLintTextAttributes.MEDIUM;
      };
    }

    if (severity != null) {
      return switch (severity) {
        case BLOCKER -> SonarLintTextAttributes.BLOCKER;
        case CRITICAL -> SonarLintTextAttributes.HIGH;
        case MINOR -> SonarLintTextAttributes.LOW;
        case INFO -> SonarLintTextAttributes.INFO;
        default -> SonarLintTextAttributes.MEDIUM;
      };
    }

    return SonarLintTextAttributes.MEDIUM;
  }

  /**
   * Must be consistent with {@link #getSeverity}.
   *
   * @see Annotation#getTextAttributes
   */
  private static ProblemHighlightType getType(@Nullable ImpactSeverity impact, @Nullable IssueSeverity severity) {
    if (severity != null) {
      return SonarLintSeverity.fromCoreSeverity(impact, severity).highlightType();
    }

    return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  }

  /**
   * Must be consistent with {@link #getType}.
   *
   * @see Annotation#getTextAttributes
   */
  private static HighlightSeverity getSeverity(@Nullable ImpactSeverity impact, @Nullable IssueSeverity severity) {
    if (severity != null) {
      return SonarLintSeverity.fromCoreSeverity(impact, severity).highlightSeverity();
    }

    return HighlightSeverity.WARNING;
  }

  public static class AnnotationContext {
  }
}
