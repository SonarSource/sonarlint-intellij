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
import org.sonarlint.intellij.actions.MarkAsResolvedAction;
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.cayc.CleanAsYouCodeService;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesCache;
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
  public void apply(@NotNull PsiFile psiFile, AnnotationContext annotationResult, @NotNull AnnotationHolder holder) {
    if (shouldSkip(psiFile)) {
      return;
    }

    var project = psiFile.getProject();
    var file = psiFile.getVirtualFile();

    var onTheFlyFindingsHolder = getService(project, AnalysisSubmitter.class).getOnTheFlyFindingsHolder();
    var issues = onTheFlyFindingsHolder.getFindingsForFile(file);
    issues.stream()
      .filter(issue -> !issue.isResolved())
      .forEach(issue -> {
        // reject ranges that are no longer valid. It probably means that they were deleted from the file, or the file was deleted
        var validTextRange = issue.getValidTextRange();
        if (validTextRange != null) {
          addAnnotation(project, issue, validTextRange, holder);
        }
      });

    // only annotate the hotspots currently displayed in the tree
    var toolWindowService = getService(project, SonarLintToolWindow.class);
    toolWindowService.getDisplayedSecurityHotspotsForFile(file)
      .forEach(securityHotspot -> {
        // reject ranges that are no longer valid. It probably means that they were deleted from the file, or the file was deleted
        var validTextRange = securityHotspot.getValidTextRange();
        if (validTextRange != null) {
          addAnnotation(project, securityHotspot, validTextRange, holder);
        }
      });

    if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
      getService(project, TaintVulnerabilitiesCache.class).getTaintVulnerabilitiesForFile(file)
        .stream().filter(vulnerability -> !vulnerability.isResolved())
        .forEach(vulnerability -> addAnnotation(project, vulnerability, holder));
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
      annotationBuilder = annotationBuilder.textAttributes(getTextAttrsKey(project, finding.getHighestImpact(), finding.getUserSeverity(), finding.isOnNewCode()));
    }

    annotationBuilder.highlightType(getType(finding.getHighestImpact(), finding.getUserSeverity()))
      .create();
  }

  private static boolean shouldSuggestQuickFix(LiveFinding issue) {
    return !SILENCED_QUICK_FIXABLE_RULE_KEYS.contains(issue.getRuleKey());
  }

  private static void addAnnotation(Project project, LocalTaintVulnerability vulnerability, AnnotationHolder annotationHolder) {
    var textRange = vulnerability.getValidTextRange();
    if (textRange == null) {
      return;
    }
    annotationHolder.newAnnotation(getSeverity(vulnerability.getHighestImpact(), vulnerability.severity()), vulnerability.message())
      .range(textRange)
      .withFix(new ShowTaintVulnerabilityRuleDescriptionIntentionAction(vulnerability))
      .withFix(new MarkAsResolvedAction(vulnerability))
      .textAttributes(getTextAttrsKey(project, vulnerability.getHighestImpact(), vulnerability.severity(), vulnerability.isOnNewCode()))
      .highlightType(getType(vulnerability.getHighestImpact(), vulnerability.severity()))
      .create();
  }

  static TextAttributesKey getTextAttrsKey(Project project, @Nullable ImpactSeverity impact, @Nullable IssueSeverity severity, boolean isOnNewCode) {
    if (getService(CleanAsYouCodeService.class).shouldFocusOnNewCode(project) && !isOnNewCode) {
      return SonarLintTextAttributes.OLD_CODE;
    }

    if (impact != null) {
      return switch (impact) {
        case HIGH -> SonarLintTextAttributes.HIGH;
        case LOW -> SonarLintTextAttributes.LOW;
        default -> SonarLintTextAttributes.MEDIUM;
      };
    }

    if (severity != null) {
      return switch (severity) {
        case CRITICAL, BLOCKER -> SonarLintTextAttributes.HIGH;
        case MINOR, INFO -> SonarLintTextAttributes.LOW;
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
