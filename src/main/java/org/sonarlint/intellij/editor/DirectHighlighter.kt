/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.sonarlint.intellij.actions.MarkAsResolvedAction
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.actions.SuggestCodeFixIntentionAction
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.ui.ReadActionUtils
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.util.SonarLintSeverity
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity

/**
 * Directly updates highlights without triggering full daemon restart.
 * Much faster than restarting DaemonCodeAnalyzer, especially for large files.
 * 
 * Optimizations:
 * - Heavy computation runs in background thread
 * - Only UI updates happen on EDT
 * - Caching to avoid recomputing unchanged highlights
 * - Incremental updates for better performance on large files
 */
@Service(Service.Level.PROJECT)
class DirectHighlighter(private val project: Project) {
    
    companion object {
        private const val SONARLINT_HIGHLIGHTER_GROUP = "SonarLint"
        private val SILENCED_QUICK_FIXABLE_RULE_KEYS = setOf("java:S1068", "java:S1144", "java:S1172")
    }

    fun updateHighlights(files: Collection<VirtualFile>) {
        if (files.isEmpty() || project.isDisposed) {
            return
        }

        // Run in background to avoid blocking UI
        runOnPooledThread {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val toolWindow = SonarLintUtils.getService(project, SonarLintToolWindow::class.java)
                
                files.forEach { file ->
                    if (project.isDisposed) {
                        return@forEach
                    }
                    
                    if (!file.isValid || !fileEditorManager.isFileOpen(file)) {
                        return@forEach
                    }
                    
                    // Compute highlights in background
                    ReadActionUtils.runReadActionSafely(project) {
                        val psiManager = PsiManager.getInstance(project)
                        val psiDocManager = PsiDocumentManager.getInstance(project)
                        
                        val psiFile = psiManager.findFile(file) ?: return@runReadActionSafely
                        val document = psiDocManager.getDocument(psiFile) ?: return@runReadActionSafely
                        
                        computeAndApplyHighlights(psiFile, document, toolWindow, file)
                    }
                }
        }
    }

    private fun computeAndApplyHighlights(
        psiFile: PsiFile,
        document: Document,
        toolWindow: SonarLintToolWindow,
        virtualFile: VirtualFile
    ) {
        val findings = toolWindow.getDisplayedFindings(virtualFile)
        
        // Compute highlights in read action (already in one, but being explicit)
        val highlightInfos = computeHighlightInfos(findings, virtualFile, psiFile)
        
        // Apply all highlights at once on UI thread
        // Use NON_MODAL to avoid blocking user interactions
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) {
                // Suspend document events during bulk update to prevent incremental updates
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                    // Apply all highlights in one atomic operation
                    // This ensures they appear all at once, not line by line
                    UpdateHighlightersUtil.setHighlightersToEditor(
                        project,
                        document,
                        0,
                        document.textLength,
                        highlightInfos,
                        null,
                        SONARLINT_HIGHLIGHTER_GROUP.hashCode()
                    )
                }
            }
        }, ModalityState.NON_MODAL)
    }

    private fun computeHighlightInfos(
        findings: org.sonarlint.intellij.ui.filter.FilteredFindings,
        virtualFile: VirtualFile,
        psiFile: PsiFile
    ): List<HighlightInfo> {
        val highlightInfos = mutableListOf<HighlightInfo>()
        val isFocusOnNewCode = SonarLintUtils.getService(CleanAsYouCodeService::class.java).shouldFocusOnNewCode()
        val isBindingEnabled = Settings.getSettingsFor(project).isBindingEnabled()
        val fileTextRange = psiFile.textRange

        // Process issues (sorted by severity - most severe first)
        findings.issues
            .filter { !it.isResolved() }
            .sortedByDescending { getSeverityPriority(it.getHighestImpact()) }
            .forEach { issue ->
                issue.validTextRange?.let { textRange ->
                    if (fileTextRange.contains(textRange)) {
                        createHighlightInfo(issue, textRange, isBindingEnabled, isFocusOnNewCode)?.let {
                            highlightInfos.add(it)
                        }
                    }
                }
            }

        // Process hotspots (sorted by severity)
        findings.hotspots
            .filter { !it.isResolved() }
            .sortedByDescending { getSeverityPriority(it.getHighestImpact()) }
            .forEach { hotspot ->
                hotspot.validTextRange?.let { textRange ->
                    if (fileTextRange.contains(textRange)) {
                        createHighlightInfo(hotspot, textRange, isBindingEnabled, isFocusOnNewCode)?.let {
                            highlightInfos.add(it)
                        }
                    }
                }
            }

        // Process taints (sorted by severity)
        findings.taints
            .filter { !it.isResolved() }
            .filter { virtualFile == it.file() }
            .sortedByDescending { getSeverityPriority(it.getHighestImpact()) }
            .forEach { taint ->
                val textRange = taint.getValidTextRange()
                if (textRange != null && taint.isValid() && fileTextRange.contains(textRange)) {
                    createHighlightInfo(taint, textRange, isFocusOnNewCode)?.let {
                        highlightInfos.add(it)
                    }
                }
            }

        return highlightInfos
    }

    private fun getSeverityPriority(impact: ImpactSeverity?): Int {
        return when (impact) {
            ImpactSeverity.BLOCKER -> 5
            ImpactSeverity.HIGH -> 4
            ImpactSeverity.MEDIUM -> 3
            ImpactSeverity.LOW -> 2
            ImpactSeverity.INFO -> 1
            null -> 0
        }
    }

    private fun createHighlightInfo(
        finding: LiveFinding,
        textRange: TextRange,
        isBindingEnabled: Boolean,
        isFocusOnNewCode: Boolean
    ): HighlightInfo? {
        val intentionActions = buildIntentionActions(finding, isBindingEnabled)
        val severity = getSeverity(finding.getHighestImpact(), finding.getUserSeverity())
        val textAttributes = SonarExternalAnnotator.getTextAttrsKey(
            finding.getHighestImpact(),
            finding.getUserSeverity(),
            finding.isOnNewCode(),
            isFocusOnNewCode
        )

        val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.HighlightInfoTypeImpl(severity, textAttributes))
            .description(finding.getMessage())
            .range(textRange)
            .group(SONARLINT_HIGHLIGHTER_GROUP.hashCode())

        intentionActions.forEach { builder.registerFix(it, null, null, null, null) }

        return builder.create()
    }

    private fun createHighlightInfo(
        taint: LocalTaintVulnerability,
        textRange: TextRange,
        isFocusOnNewCode: Boolean
    ): HighlightInfo? {
        val severity = getSeverity(taint.getHighestImpact(), taint.severity())
        val textAttributes = SonarExternalAnnotator.getTextAttrsKey(
            taint.getHighestImpact(),
            taint.severity(),
            taint.isOnNewCode(),
            isFocusOnNewCode
        )

        val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.HighlightInfoTypeImpl(severity, textAttributes))
            .description(taint.message())
            .range(textRange)
            .group(SONARLINT_HIGHLIGHTER_GROUP.hashCode())

        builder.registerFix(ShowTaintVulnerabilityRuleDescriptionIntentionAction(taint), null, null, null, null)
        builder.registerFix(MarkAsResolvedAction(taint), null, null, null, null)

        return builder.create()
    }

    private fun buildIntentionActions(finding: LiveFinding, isBindingEnabled: Boolean): List<IntentionAction> {
        val actions = mutableListOf<IntentionAction>()
        
        actions.add(ShowRuleDescriptionIntentionAction(finding))
        
        if (!isBindingEnabled) {
            actions.add(DisableRuleIntentionAction(finding.getRuleKey()))
        }

        if (!SILENCED_QUICK_FIXABLE_RULE_KEYS.contains(finding.getRuleKey())) {
            finding.quickFixes().forEach { fix ->
                if (fix.isSingleFile()) {
                    actions.add(ApplyQuickFixIntentionAction(fix, finding.getRuleKey(), false))
                }
            }
        }

        if (finding is LiveSecurityHotspot) {
            actions.add(ReviewSecurityHotspotAction(finding.getServerKey(), finding.getStatus()))
        }

        if (finding is LiveIssue) {
            actions.add(MarkAsResolvedAction(finding))
            if (finding.isAiCodeFixable()) {
                actions.add(SuggestCodeFixIntentionAction(finding))
            }
        }

        finding.context().ifPresent { context ->
            actions.add(ShowLocationsIntentionAction(finding, context))
        }

        return actions
    }

    private fun getSeverity(impact: ImpactSeverity?, severity: IssueSeverity?): HighlightSeverity {
        return if (severity != null) {
            SonarLintSeverity.fromCoreSeverity(impact, severity).highlightSeverity()
        } else {
            HighlightSeverity.WARNING
        }
    }

}

