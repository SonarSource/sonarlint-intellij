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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.NonInjectable
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
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
class DirectHighlighter @NonInjectable internal constructor(
    private val project: Project,
    private val scheduler: ScheduledExecutorService,
    private val debounceDelayMs: Long,
) : Disposable {

    constructor(project: Project) : this(project, newScheduler(project), DEFAULT_DEBOUNCE_DELAY_MS)

    private val queueLock = Any()
    private val pendingFiles = linkedSetOf<VirtualFile>()
    private val latestGenerationByFile = mutableMapOf<VirtualFile, Long>()
    private var nextGeneration = 0L
    private var scheduledTask: ScheduledFuture<*>? = null
    private var disposed = false

    /**
     * Recomputes and writes the SonarQube highlights for the given [files]. Called (debounced) from
     * [CodeAnalyzerRestarter] whenever the displayed findings change.
     *
     * Requests are coalesced and prepared serially on a project-owned worker. Only the final markup update is posted to
     * the EDT. Each request gets a generation so an obsolete prepared result cannot overwrite a newer one. Keeping
     * [UpdateHighlightersUtil.setHighlightersToEditor] off the read-action path avoids "slow operations on EDT"
     * assertions in integration tests.
     */
    fun updateHighlights(files: Collection<VirtualFile>) {
        if (files.isEmpty() || project.isDisposed) {
            return
        }

        synchronized(queueLock) {
            if (disposed) {
                return
            }
            files.forEach { file ->
                latestGenerationByFile[file] = ++nextGeneration
                pendingFiles.add(file)
            }
            scheduledTask?.cancel(false)
            scheduledTask = scheduler.schedule(::processPendingFiles, debounceDelayMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun processPendingFiles() {
        val requests = synchronized(queueLock) {
            scheduledTask = null
            pendingFiles.mapNotNull { file ->
                latestGenerationByFile[file]?.let { generation -> HighlightRequest(file, generation) }
            }.also { pendingFiles.clear() }
        }
        if (requests.isEmpty() || project.isDisposed) {
            return
        }

        val preparedHighlights = requests.mapNotNull { request ->
            if (!isLatest(request)) {
                null
            } else {
                val prepared = prepareHighlights(request)
                if (prepared == null) {
                    complete(request)
                    null
                } else if (!isLatest(request)) {
                    // A newer request may have arrived while the read action was running. The EDT guard below remains
                    // authoritative, but avoiding the post here saves unnecessary work in the common case.
                    null
                } else {
                    prepared
                }
            }
        }
        if (preparedHighlights.isEmpty() || project.isDisposed) {
            return
        }
        runOnUiThread(project, ModalityState.nonModal()) {
            preparedHighlights.forEach { applyPreparedHighlightsIfCurrent(it) }
        }
    }

    @VisibleForTesting
    internal fun applyHighlightsForTest(file: VirtualFile) {
        val request = synchronized(queueLock) {
            val generation = ++nextGeneration
            latestGenerationByFile[file] = generation
            HighlightRequest(file, generation)
        }
        prepareHighlights(request)?.let { applyPreparedHighlightsIfCurrent(it) }
    }

    /** Collects highlight descriptors under read lock; must not write editor markup here. */
    private fun prepareHighlights(request: HighlightRequest): PreparedHighlights? {
        return computeReadActionSafely(request.file, project) {
            val file = request.file
            if (project.isDisposed || !file.isValid || !FileEditorManager.getInstance(project).isFileOpen(file)) {
                return@computeReadActionSafely null
            }
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@computeReadActionSafely null
            val fileRange = TextRange(0, document.textLength)
            val highlights = collectHighlightPlans(file).mapNotNull { it.toHighlightInfo(fileRange) }
            PreparedHighlights(request, document, document.modificationStamp, highlights)
        }
    }

    /** Writes current pre-computed highlights into the editor; must run on the EDT, outside any read action. */
    private fun applyPreparedHighlightsIfCurrent(prepared: PreparedHighlights) {
        val request = prepared.request
        if (project.isDisposed || !isLatest(request)) {
            return
        }
        val file = request.file
        val document = prepared.document
        if (!file.isValid || !FileEditorManager.getInstance(project).isFileOpen(file)) {
            complete(request)
            return
        }
        if (document.modificationStamp != prepared.documentModificationStamp) {
            // The prepared HighlightInfo instances contain fixed offsets. Recompute from the findings' RangeMarkers
            // instead of applying them to a newer document revision.
            updateHighlights(listOf(file))
            return
        }
        synchronized(queueLock) {
            // Keep the final generation check and markup write atomic with respect to registering a new request. A
            // request arriving during the write will therefore be ordered after this application and refresh it again.
            if (disposed || latestGenerationByFile[file] != request.generation) {
                return
            }
            UpdateHighlightersUtil.setHighlightersToEditor(
                project, document, 0, document.textLength, prepared.highlights, null, SONARLINT_GROUP
            )
            if (file !in pendingFiles) {
                latestGenerationByFile.remove(file)
            }
        }
    }

    private fun isLatest(request: HighlightRequest): Boolean = synchronized(queueLock) {
        latestGenerationByFile[request.file] == request.generation
    }

    private fun complete(request: HighlightRequest) {
        synchronized(queueLock) {
            if (latestGenerationByFile[request.file] == request.generation && request.file !in pendingFiles) {
                latestGenerationByFile.remove(request.file)
            }
        }
    }

    override fun dispose() {
        synchronized(queueLock) {
            disposed = true
            scheduledTask?.cancel(false)
            scheduledTask = null
            pendingFiles.clear()
            latestGenerationByFile.clear()
        }
        scheduler.shutdownNow()
    }

    private data class HighlightRequest(val file: VirtualFile, val generation: Long)

    private data class PreparedHighlights(
        val request: HighlightRequest,
        val document: Document,
        val documentModificationStamp: Long,
        val highlights: List<HighlightInfo>,
    )

    /**
     * Builds the list of highlights to render for [file] from the findings currently displayed in the tool window.
     * Reads the shared snapshot rather than the raw analysis so that editor highlights always match what the user
     * sees in the Current File tab (same filtering, same resolved/new-code handling).
     */
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
        private const val DEFAULT_DEBOUNCE_DELAY_MS = 100L

        private fun newScheduler(project: Project): ScheduledExecutorService =
            ScheduledThreadPoolExecutor(1) { runnable ->
                Thread(runnable, "sonarlint-direct-highlighter-${project.name}").apply { isDaemon = true }
            }.apply { removeOnCancelPolicy = true }

        // Dedicated highlighter group id used exclusively for SonarQube findings. It must stay distinct from the
        // (small) group ids used by IntelliJ's own daemon passes so that neither clears the other's highlights. The
        // exact value is arbitrary but must remain stable; 0x53_4C_49 spells the ASCII bytes 'S','L','I'.
        private const val SONARLINT_GROUP = 0x53_4C_49

        // Some quick fixes do not match the IntelliJ experience
        private val SILENCED_QUICK_FIXABLE_RULE_KEYS = setOf("java:S1068", "java:S1144", "java:S1172")
    }

}
