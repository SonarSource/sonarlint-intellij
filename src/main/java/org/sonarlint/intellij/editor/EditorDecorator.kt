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
package org.sonarlint.intellij.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import org.sonarlint.intellij.config.SonarLintTextAttributes
import org.sonarlint.intellij.issue.Flow
import org.sonarlint.intellij.issue.LiveIssue
import org.sonarlint.intellij.issue.Location
import org.sonarlint.intellij.issue.hotspot.LocalHotspot
import org.sonarlint.intellij.issue.vulnerabilities.LocalTaintVulnerability
import java.awt.Font
import java.util.function.Consumer

private const val HIGHLIGHT_GROUP_ID = 1001

open class EditorDecorator(private val project: Project) {
  private val currentHighlightedDoc: HashSet<Document> = hashSetOf()
  private var blinker: RangeBlinker? = null

  open fun removeHighlights() {
    currentHighlightedDoc.forEach {
      clearSecondaryLocationNumbers(it)
      UpdateHighlightersUtil.setHighlightersToEditor(project, it, 0, it.textLength, emptyList(), null, HIGHLIGHT_GROUP_ID)
    }
    currentHighlightedDoc.clear()
    stopBlinking()
  }

  private fun stopBlinking() {
    blinker?.stopBlinking()
    blinker = null
  }

  open fun highlightFlow(flow: Flow) {
    updateHighlights(createHighlights(flow.locations))
    displaySecondaryLocationNumbers(flow, null)
  }

  private fun displaySecondaryLocationNumbers(flow: Flow, selectedLocation: Location?) {
    flow.locations.forEachIndexed { index, location ->
      drawSecondaryLocationNumbers(location.range, index + 1, selectedLocation != null && selectedLocation == location)
    }
  }

  open fun highlightIssue(issue: LiveIssue) {
    val highlights = issue.context()
      .map { createHighlights(it.flows()[0].locations) }
      .orElse(mutableListOf())
    createHighlight(issue.range, issue.message)?.let(highlights::add)
    updateHighlights(highlights)
    issue.context().ifPresent { displaySecondaryLocationNumbers(it.flows()[0], null) }
  }

  open fun highlight(vulnerability: LocalTaintVulnerability) {
    val highlights = createHighlights(vulnerability.flows[0].locations)
    createHighlight(vulnerability.rangeMarker(), vulnerability.message())?.let(highlights::add)
    updateHighlights(highlights)
    displaySecondaryLocationNumbers(vulnerability.flows[0], null)
  }

  open fun highlightPrimaryLocation(rangeMarker: RangeMarker, message: String?, associatedFlow: Flow) {
    val highlights = createHighlights(associatedFlow.locations)
    createHighlight(rangeMarker, message)?.let(highlights::add)
    updateHighlights(highlights)
    displaySecondaryLocationNumbers(associatedFlow, null)
  }

  open fun highlightSecondaryLocation(secondaryLocation: Location, parentFlow: Flow) {
    secondaryLocation.range ?: return
    val highlights = createHighlights(parentFlow.locations)
    createHighlight(secondaryLocation.range, secondaryLocation.message)?.let(highlights::add)
    updateHighlights(highlights)
    displaySecondaryLocationNumbers(parentFlow, secondaryLocation)
  }

  open fun highlight(hotspot: LocalHotspot) {
    createHighlight(hotspot.primaryLocation.range, hotspot.message)?.let { updateHighlights(listOf(it)) }
  }

  private fun updateHighlights(highlights: List<Highlight>) {
    removeHighlights()
    highlights.groupBy({ it.document }, { it.highlightInfo })
      .forEach { (doc, hs) ->
        highlightInDocument(hs, doc)
        blinkLocations(hs, doc)
      }
  }

  private fun highlightInDocument(highlights: List<HighlightInfo?>, document: Document) {
    UpdateHighlightersUtil.setHighlightersToEditor(project, document, 0,
      document.textLength, highlights, null, HIGHLIGHT_GROUP_ID)
    currentHighlightedDoc.add(document)
  }

  private fun clearSecondaryLocationNumbers(document: Document) {
    getEditors(document)
      .forEach {
        it.inlayModel.getInlineElementsInRange(0, document.textLength, SecondaryLocationIndexRenderer::class.java)
          .forEach { disposable -> Disposer.dispose(disposable!!) }
      }
  }

  private fun drawSecondaryLocationNumbers(rangeMarker: RangeMarker?, index: Int, selected: Boolean) {
    val marker = rangeMarker ?: return
    if (!marker.isValid) {
      return;
    }
    getEditors(marker.document)
      .forEach { it.inlayModel.addInlineElement(marker.startOffset, SecondaryLocationIndexRenderer(index, selected)) }
  }

  private fun getEditors(document: Document): List<Editor> {
    return EditorFactory.getInstance().getEditors(document, project).toList()
  }

  private fun blinkLocations(highlights: List<HighlightInfo>, document: Document) {
    if (highlights.isEmpty()) {
      return
    }
    getEditors(document).forEach(Consumer { editor: Editor ->
      blinker = RangeBlinker(editor, TextAttributes(null, null, JBColor.YELLOW, EffectType.BOXED, Font.PLAIN), 3)
      blinker!!.blinkHighlights(highlights)
    })
  }

  open fun isActiveInEditor(editor: Editor): Boolean {
    return currentHighlightedDoc.contains(editor.document)
  }

  private fun createHighlights(locations: List<Location>): MutableList<Highlight> {
    return locations
      .mapNotNull { createHighlight(it.range, it.message) }
      .toMutableList()
  }

  private fun createHighlight(location: RangeMarker?, message: String?): Highlight? {
    if (location == null) {
      return null
    }
    // Creating the HighlightInfo with high severity will ensure that it will override most other highlighters.
    val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(location.startOffset, location.endOffset)
      .severity(HighlightSeverity.ERROR)
      .textAttributes(SonarLintTextAttributes.SELECTED)
    if (message != null && message.isNotEmpty() && "..." != message) {
      builder.descriptionAndTooltip("SonarLint: $message")
    }
    return builder.create()?.let { Highlight(location.document, it) }
  }

  class Highlight(val document: Document, val highlightInfo: HighlightInfo)
}