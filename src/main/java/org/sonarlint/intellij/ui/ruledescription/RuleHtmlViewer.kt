/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JEditorPane
import javax.swing.ScrollPaneConstants
import javax.swing.text.DefaultCaret
import javax.swing.text.html.HTMLEditorKit


class RuleHtmlViewer(scrollable: Boolean) : JBPanel<RuleHtmlViewer>(BorderLayout()) {
    private var editor: JEditorPane = JEditorPane().apply {
        contentType = UIUtil.HTML_MIME
        (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
        editorKit = UIUtil.getHTMLEditorKit()
        border = JBUI.Borders.empty(10)
        isEditable = false
        isOpaque = false

        addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    }

    init {
        prepareCSS(editor.editorKit as HTMLEditorKit)
        if (scrollable) {
            val scrollableRulePanel = ScrollPaneFactory.createScrollPane(editor, true)
            scrollableRulePanel.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            scrollableRulePanel.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            scrollableRulePanel.verticalScrollBar.unitIncrement = 10
            scrollableRulePanel.isOpaque = false
            scrollableRulePanel.viewport.isOpaque = false
            add(scrollableRulePanel, BorderLayout.CENTER)
        } else {
            add(editor, BorderLayout.CENTER)
        }
    }

    private fun prepareCSS(editorKit: HTMLEditorKit) {
        editorKit.styleSheet.addRule("h2 { font-size: 150%; }")
        editorKit.styleSheet.addRule("h3 { font-size: 130%; }")
        editorKit.styleSheet.addRule("h4 { font-size: 110%; }")
    }


    fun clear() {
        editor.text = ""
    }

    fun updateHtml(html: String) {
        SwingHelper.setHtml(editor, html, UIUtil.getLabelForeground())
    }


}