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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.RawIssueAdapter
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.trigger.TriggerType
import org.sonarlint.intellij.util.VirtualFileUtils.uriToVirtualFile
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto


class AnalysisState(
    val id: UUID,
    private val analysisCallback: AnalysisCallback,
    private val filesToAnalyze: MutableCollection<VirtualFile>,
    private val module: Module,
    private val triggerType: TriggerType,
) {
    private val modificationStampByFile = ConcurrentHashMap<VirtualFile, Long>()
    private val analysisDate: Instant = Instant.now()
    private val liveIssues = mutableMapOf<VirtualFile, Collection<LiveIssue>>()
    private val liveHotspots = mutableMapOf<VirtualFile, Collection<LiveSecurityHotspot>>()
    private val shouldReceiveHotspot: Boolean
    private var hasReceivedFinalIssues = false
    private var hasReceivedFinalHotspots = false

    init {
        this.initFiles(filesToAnalyze)
        shouldReceiveHotspot = Settings.getSettingsFor(module.project).isBound
    }

    private fun initFiles(files: Collection<VirtualFile>) {
        files.forEach { file: VirtualFile ->
            computeReadActionSafely(file, module.project) {
                FileDocumentManager.getInstance().getDocument(file)
            }?.let { modificationStampByFile[file] = it.modificationStamp }
        }
    }

    fun addRawHotspots(analysisId: UUID, hotspotsByFile: Map<URI, List<RaisedHotspotDto>>, isIntermediate: Boolean) {
        hasReceivedFinalHotspots = !isIntermediate

        liveHotspots.putAll(hotspotsByFile.mapNotNull { (uri, rawHotspots) ->
            val virtualFile = uriToVirtualFile(uri)
            if (virtualFile != null) {
                val liveHotspots = convertRawHotspots(virtualFile, rawHotspots)
                virtualFile to liveHotspots
            } else {
                null
            }
        })

        if (isAnalysisFinished()) {
            analysisCallback.onSuccess(
                AnalysisResult(
                    analysisId,
                    LiveFindings(liveIssues, liveHotspots),
                    filesToAnalyze,
                    triggerType,
                    analysisDate
                )
            )
        } else {
            analysisCallback.onIntermediateResult(AnalysisIntermediateResult(LiveFindings(liveIssues, liveHotspots)))
        }
    }

    fun addRawIssues(analysisId: UUID, issuesByFile: Map<URI, List<RaisedIssueDto>>, isIntermediate: Boolean) {
        hasReceivedFinalIssues = !isIntermediate

        liveIssues.putAll(issuesByFile.mapNotNull { (uri, rawIssues) ->
            val virtualFile = uriToVirtualFile(uri)
            if (virtualFile != null) {
                val liveIssues = convertRawIssues(virtualFile, rawIssues)
                virtualFile to liveIssues
            } else {
                null
            }
        })

        if (isAnalysisFinished()) {
            analysisCallback.onSuccess(
                AnalysisResult(
                    analysisId,
                    LiveFindings(liveIssues, liveHotspots),
                    filesToAnalyze,
                    triggerType,
                    analysisDate
                )
            )
        } else {
            analysisCallback.onIntermediateResult(AnalysisIntermediateResult(LiveFindings(liveIssues, liveHotspots)))
        }
    }

    private fun convertRawHotspots(virtualFile: VirtualFile, rawHotspots: Collection<RaisedHotspotDto>): Collection<LiveSecurityHotspot> {
        try {
            return rawHotspots.mapNotNull { hotspot ->
                RawIssueAdapter.toLiveSecurityHotspot(
                    module,
                    hotspot,
                    virtualFile,
                    modificationStampByFile[virtualFile]
                )
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            SonarLintConsole.get(module.project).error("Error finding location for Security Hotspot", e)
        }
        return emptyList()
    }

    private fun convertRawIssues(virtualFile: VirtualFile, rawIssues: Collection<RaisedIssueDto>): Collection<LiveIssue> {
        try {
            return rawIssues.mapNotNull { issue ->
                RawIssueAdapter.toLiveIssue(
                    module,
                    issue,
                    virtualFile,
                    modificationStampByFile[virtualFile]
                )
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            SonarLintConsole.get(module.project).error("Error finding location for issue", e)
        }
        return emptyList()
    }

    fun isAnalysisFinished(): Boolean {
        return hasReceivedFinalIssues && (!shouldReceiveHotspot || hasReceivedFinalHotspots)
    }

}
