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
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import java.awt.Font
import java.util.function.Consumer
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.SonarLintTextAttributes
import org.sonarlint.intellij.finding.Flow
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.finding.Location
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.codefix.CodeFixGutterHandler
import org.sonarlint.intellij.ui.codefix.CodeFixGutterIconRenderer
import org.sonarlint.intellij.util.getDocument

private const val HIGHLIGHT_GROUP_ID = 1001

@Service(Service.Level.PROJECT)
class EditorDecorator(private val project: Project) : Disposable {
    private val currentHighlightedDoc: MutableSet<Document> = hashSetOf()
    private var blinker: RangeBlinker? = null

    fun removeHighlights() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        currentHighlightedDoc.forEach {
            clearSecondaryLocationNumbers(it)
            if (!project.isDisposed) {
                UpdateHighlightersUtil.setHighlightersToEditor(
                    project, it, 0, it.textLength, emptyList(), null, HIGHLIGHT_GROUP_ID
                )
            }
        }
        currentHighlightedDoc.clear()
        stopBlinking()
    }

    private fun stopBlinking() {
        blinker?.stopBlinking()
        blinker = null
    }

    fun highlightFlow(flow: Flow) {
        updateHighlights(createHighlights(flow.locations))
        displaySecondaryLocationNumbers(flow, null)
    }

    private fun displaySecondaryLocationNumbers(flow: Flow, selectedLocation: Location?) {
        flow.locations.forEachIndexed { index, location ->
            drawSecondaryLocationNumbers(location, index + 1, selectedLocation != null && selectedLocation == location)
        }
    }

    fun highlightFinding(finding: LiveFinding) {
        val highlights = finding.context()
            .map { createHighlights(it.flows()[0].locations) }
            .orElse(mutableListOf())
        createHighlight(finding.range, finding.message)?.let(highlights::add)
        updateHighlights(highlights)
        finding.context().ifPresent { displaySecondaryLocationNumbers(it.flows()[0], null) }
    }

    fun highlightRange(range: RangeMarker) {
        createHighlight(range, null)?.let {
            updateHighlights(listOf(it))
        }
    }

    fun highlight(vulnerability: LocalTaintVulnerability) {
        val highlights = createHighlights(vulnerability.flows[0].locations)
        createHighlight(vulnerability.rangeMarker(), vulnerability.message())?.let(highlights::add)
        updateHighlights(highlights)
        displaySecondaryLocationNumbers(vulnerability.flows[0], null)
    }

    fun highlightPrimaryLocation(rangeMarker: RangeMarker, message: String?, associatedFlow: Flow) {
        val highlights = createHighlights(associatedFlow.locations)
        createHighlight(rangeMarker, message)?.let(highlights::add)
        updateHighlights(highlights)
        displaySecondaryLocationNumbers(associatedFlow, null)
    }

    fun highlightSecondaryLocation(secondaryLocation: Location, parentFlow: Flow) {
        secondaryLocation.range ?: return
        val highlights = createHighlights(parentFlow.locations)
        createHighlight(secondaryLocation.range, secondaryLocation.message)?.let(highlights::add)
        updateHighlights(highlights)
        displaySecondaryLocationNumbers(parentFlow, secondaryLocation)
    }

    private fun updateHighlights(highlights: List<Highlight>) {
        runOnUiThread(project) {
            removeHighlights()
            highlights.groupBy({ it.document }, { it.highlightInfo })
                .forEach { (doc, hs) ->
                    highlightInDocument(hs, doc)
                    blinkLocations(hs, doc)
                }
        }
    }

    private fun highlightInDocument(highlights: List<HighlightInfo?>, document: Document) {
        UpdateHighlightersUtil.setHighlightersToEditor(
            project, document, 0,
            document.textLength, highlights, null, HIGHLIGHT_GROUP_ID
        )
        currentHighlightedDoc.add(document)
    }

    private fun clearSecondaryLocationNumbers(document: Document) {
        getEditors(document)
            .forEach {
                it.inlayModel.getInlineElementsInRange(0, document.textLength, SecondaryLocationIndexRenderer::class.java)
                    .forEach { disposable -> Disposer.dispose(disposable!!) }
            }
    }

    private fun drawSecondaryLocationNumbers(location: Location, index: Int, selected: Boolean) {
        if (!location.exists()) {
            return
        }
        val marker = location.range!!
        runOnUiThread(project) {
            getEditors(marker.document).forEach {
                it.inlayModel.addInlineElement(
                    marker.startOffset,
                    SecondaryLocationIndexRenderer(location, index, selected)
                )
            }
        }
    }

    private fun getEditors(document: Document): List<Editor> {
        return EditorFactory.getInstance().getEditors(document, project).toList()
    }

    private fun blinkLocations(highlights: List<HighlightInfo>, document: Document) {
        if (highlights.isEmpty()) {
            return
        }
        getEditors(document).forEach(Consumer { editor: Editor ->
            blinker = RangeBlinker(editor, TextAttributes(null, null, JBColor.YELLOW, EffectType.BOXED, Font.PLAIN), 3, project)
            blinker!!.blinkHighlights(highlights)
        })
    }

    fun isActiveInEditor(editor: Editor): Boolean {
        return editor.document in currentHighlightedDoc
    }

    private fun createGutterIcon(editor: Editor, startOffset: Int, line: Int, issues: List<Issue>): RangeHighlighter {
        val renderer = CodeFixGutterIconRenderer(editor, line, issues)
        return editor.markupModel.addRangeHighlighter(
            null,
            startOffset,
            startOffset,
            HighlighterLayer.LAST,
            HighlighterTargetArea.LINES_IN_RANGE
        ).apply {
            gutterIconRenderer = renderer
        }
    }

    // Taints comes from multiple files, unlike issues that are found for the current file opened
    // We should probably refresh the gutter icons when a new file is selected, as we can only add the icons on the current editor
    fun createGutterIconForTaints(taints: Collection<LocalTaintVulnerability>) {
        if (taints.isEmpty()) {
            return
        }

        val selectedFiles = FileEditorManager.getInstance(project).selectedFiles
        val fixableTaintsByFile = taints
            .filter { it.isAiCodeFixable() && it.rangeMarker() != null && it.file() != null && selectedFiles.contains(it.file()) }
            .groupBy { it.file() }

        fixableTaintsByFile.forEach { (file, taints) ->
            val document = file?.getDocument() ?: return@forEach
            val fixableTaintsByLine = taints.groupBy {
                val marker = it.rangeMarker() ?: return@forEach
                if (marker.startOffset > document.textLength || marker.endOffset > document.textLength) {
                    return@forEach
                }
                document.getLineNumber(marker.startOffset)
            }

            getEditors(document).forEach { editor ->
                getService(project, CodeFixGutterHandler::class.java).cleanTaintIconsFromDisposedEditorsAndSelectedEditor(editor)
                if (!editor.isDisposed) {
                    val icons = fixableTaintsByLine.map { (line, fixableTaints) ->
                        val startOffset = taints.first().rangeMarker()?.startOffset ?: return
                        createGutterIcon(
                            editor,
                            startOffset,
                            line,
                            fixableTaints
                        )
                    }.toSet()
                    getService(project, CodeFixGutterHandler::class.java).addTaintIcons(editor, icons)
                }
            }
        }
    }

    fun createGutterIconForIssues(file: VirtualFile, issues: Collection<LiveIssue>) {
        val document = file.getDocument() ?: return

        val fixableIssuesByLine = issues
            .filter { it.isAiCodeFixable() && it.range != null }
            .groupBy { document.getLineNumber(it.range!!.startOffset) }

        getEditors(document).forEach { editor ->
            getService(project, CodeFixGutterHandler::class.java).cleanIssueIconsFromDisposedEditorsAndSelectedEditor(editor)
            val icons = fixableIssuesByLine.map { (line, fixableIssues) ->
                val startOffset = fixableIssues.first().range?.startOffset ?: return
                createGutterIcon(
                    editor,
                    startOffset,
                    line,
                    fixableIssues
                )
            }.toSet()
            getService(project, CodeFixGutterHandler::class.java).addIssueIcons(editor, icons)
        }
    }

    private fun createHighlights(locations: List<Location>): MutableList<Highlight> {
        return locations
            .mapNotNull { createHighlight(it.range, it.message) }
            .toMutableList()
    }

    private fun locationInvalid(location: RangeMarker?): Boolean {
        return location == null || !location.isValid || location.startOffset == location.endOffset
    }

    private fun createHighlight(location: RangeMarker?, message: String?): Highlight? {
        if (locationInvalid(location)) {
            return null
        }
        // Creating the HighlightInfo with high severity will ensure that it will override most other highlighters.
        val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(location!!.startOffset, location.endOffset)
            .severity(HighlightSeverity.ERROR)
            .textAttributes(SonarLintTextAttributes.SELECTED)
        if (!message.isNullOrEmpty() && "..." != message) {
            builder.descriptionAndTooltip("SonarQube: $message")
        }
        return builder.create()?.let { hl -> computeReadActionSafely { Highlight(location.document, hl) } }
    }

    override fun dispose() {
        stopBlinking()
        removeHighlights()
    }

    class Highlight(val document: Document, val highlightInfo: HighlightInfo)
}
