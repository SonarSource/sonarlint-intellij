/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.VisibleForTesting
import org.sonarlint.intellij.actions.MarkAsResolvedAction
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction
import org.sonarlint.intellij.actions.SuggestCodeFixIntentionAction
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.editor.DirectHighlighter.Companion.SONARLINT_GROUP
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.currentfile.CurrentFileDisplayedFindingsStore
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity

/**
 * Renders SonarQube findings directly into the editor markup, bypassing IntelliJ's code analysis pipeline.
 *
 * Findings used to be displayed through a low-priority [com.intellij.lang.annotation.ExternalAnnotator], which only
 * runs once the daemon has finished every other highlighting pass. On large files with many findings this delayed the
 * highlighting by several seconds. Writing the highlights ourselves makes them appear as soon as the analysis result
 * is available, independently of the daemon.
 *
 * Highlights are written under a dedicated [SONARLINT_GROUP] so the daemon's own passes never touch them, and they are
 * refreshed whenever the displayed findings change (see [CodeAnalyzerRestarter]).
 */
@Service(Service.Level.PROJECT)
class DirectHighlighter(private val project: Project) {

    fun updateHighlights(files: Collection<VirtualFile>) {
        if (files.isEmpty() || project.isDisposed) {
            return
        }
        runOnPooledThread(project) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            files.toSet().forEach { file ->
                if (project.isDisposed || !file.isValid || !fileEditorManager.isFileOpen(file)) {
                    return@forEach
                }
                runOnUiThread(project, ModalityState.nonModal()) {
                    applyHighlights(file)
                }
            }
        }
    }

    private fun applyHighlights(file: VirtualFile) {
        computeReadActionSafely(file, project) {
            if (project.isDisposed || !file.isValid || !FileEditorManager.getInstance(project).isFileOpen(file)) {
                return@computeReadActionSafely
            }
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@computeReadActionSafely
            val fileRange = TextRange(0, document.textLength)
            val highlights = collectHighlightPlans(file).mapNotNull { it.toHighlightInfo(fileRange) }
            UpdateHighlightersUtil.setHighlightersToEditor(
                project, document, 0, document.textLength, highlights, null, SONARLINT_GROUP
            )
        }
    }

    @VisibleForTesting
    internal fun applyHighlightsForTest(file: VirtualFile) {
        applyHighlights(file)
    }

    private fun collectHighlightPlans(file: VirtualFile): List<HighlightPlan> {
        val findings = getService(project, CurrentFileDisplayedFindingsStore::class.java).getFindingsForFile(file)
        val isFocusOnNewCode = getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()
        val isBindingEnabled = getSettingsFor(project).isBindingEnabled
        val plans = mutableListOf<HighlightPlan>()

        (findings.issues.asSequence() + findings.hotspots.asSequence())
            .filter { !it.isResolved() }
            .forEach { finding ->
                plans.add(
                    FindingHighlightPlan(
                        finding,
                        isFocusOnNewCode,
                        intentionActionsFor(finding, isBindingEnabled),
                    )
                )
            }

        findings.taints
            .filter { !it.isResolved() && file == it.file() }
            .forEach { taint -> plans.add(TaintHighlightPlan(taint, isFocusOnNewCode)) }

        return plans
    }

    private fun newBuilder(
        impact: ImpactSeverity?, severity: IssueSeverity?,
        isOnNewCode: Boolean, isFocusOnNewCode: Boolean, message: String, isFileLevel: Boolean, textRange: TextRange,
    ): HighlightInfo.Builder {
        val highlightSeverity = SonarFindingHighlighting.getSeverity(impact, severity)
        // Matches former ExternalAnnotator behaviour: GENERIC_ERROR_OR_WARNING maps via convertSeverity.
        val builder = HighlightInfo.newHighlightInfo(HighlightInfo.convertSeverity(highlightSeverity))
            .severity(highlightSeverity)
            .descriptionAndTooltip(message)
            .range(textRange)
            .group(SONARLINT_GROUP)
        if (isFileLevel) {
            builder.fileLevelAnnotation()
        } else {
            builder.textAttributes(SonarFindingHighlighting.getTextAttrsKey(impact, severity, isOnNewCode, isFocusOnNewCode))
        }
        return builder
    }

    private fun intentionActionsFor(finding: LiveFinding, isBindingEnabled: Boolean): List<IntentionAction> {
        val actions = mutableListOf<IntentionAction>()
        actions.add(ShowRuleDescriptionIntentionAction(finding))
        if (!isBindingEnabled) {
            actions.add(DisableRuleIntentionAction(finding.getRuleKey()))
        }
        if (!SILENCED_QUICK_FIXABLE_RULE_KEYS.contains(finding.getRuleKey())) {
            finding.quickFixes().filter { it.isSingleFile() }.forEach { actions.add(ApplyQuickFixIntentionAction(it, finding.getRuleKey(), false)) }
        }
        if (finding is LiveSecurityHotspot) {
            actions.add(ReviewSecurityHotspotAction(finding.getServerKey(), finding.status))
        }
        if (finding is LiveIssue) {
            actions.add(MarkAsResolvedAction(finding))
            if (finding.isAiCodeFixable()) {
                actions.add(SuggestCodeFixIntentionAction(finding))
            }
        }
        finding.context().ifPresent { actions.add(ShowLocationsIntentionAction(finding, it)) }
        return actions
    }

    private sealed interface HighlightPlan {
        fun toHighlightInfo(fileRange: TextRange): HighlightInfo?
    }

    private inner class FindingHighlightPlan(
        private val finding: LiveFinding,
        private val isFocusOnNewCode: Boolean,
        private val intentionActions: List<IntentionAction>,
    ) : HighlightPlan {
        override fun toHighlightInfo(fileRange: TextRange): HighlightInfo? {
            val textRange = finding.validTextRange ?: return null
            if (!fileRange.contains(textRange)) {
                return null
            }
            val builder = newBuilder(
                finding.getHighestImpact(), finding.userSeverity, finding.isOnNewCode(), isFocusOnNewCode,
                finding.message, finding.range == null, textRange,
            )
            intentionActions.forEach { builder.registerFix(it, null, null, null, null) }
            return builder.create()
        }
    }

    private inner class TaintHighlightPlan(
        private val taint: LocalTaintVulnerability,
        private val isFocusOnNewCode: Boolean,
    ) : HighlightPlan {
        override fun toHighlightInfo(fileRange: TextRange): HighlightInfo? {
            val textRange = taint.getValidTextRange() ?: return null
            if (!taint.isValid() || !fileRange.contains(textRange)) {
                return null
            }
            val builder = newBuilder(
                taint.getHighestImpact(), taint.severity(), taint.isOnNewCode(), isFocusOnNewCode,
                taint.message(), false, textRange,
            )
            builder.registerFix(ShowTaintVulnerabilityRuleDescriptionIntentionAction(taint), null, null, null, null)
            builder.registerFix(MarkAsResolvedAction(taint), null, null, null, null)
            return builder.create()
        }
    }

    companion object {
        // Dedicated highlighter group so the daemon's highlighting passes never remove SonarQube findings.
        private val SONARLINT_GROUP = "SonarLintFindings".hashCode()

        // Some quick fixes do not match the IntelliJ experience
        private val SILENCED_QUICK_FIXABLE_RULE_KEYS = setOf("java:S1068", "java:S1144", "java:S1172")
    }

}
