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

import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.core.RuleDescription
import java.awt.BorderLayout
import javax.swing.JEditorPane
import javax.swing.ScrollPaneConstants
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret


class RuleHtmlViewer(private val project: Project) : JBPanel<RuleHtmlViewer>(BorderLayout()) {
    private var editor: JEditorPane
    private var ruleDescriptionHyperLinkListener: RuleDescriptionHyperLinkListener
    private var currentRuleKey: String? = null
    private var currentModule: Module? = null

    init {
        editor = JEditorPane().apply {
            contentType = UIUtil.HTML_MIME
            (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
            editorKit = RuleDescriptionHTMLEditorKit()
            border = JBUI.Borders.empty(10)
            isEditable = false
            isOpaque = false

            ruleDescriptionHyperLinkListener = RuleDescriptionHyperLinkListener(project)
            addHyperlinkListener(ruleDescriptionHyperLinkListener)
        }
        val scrollableRulePanel = ScrollPaneFactory.createScrollPane(editor, true)
        scrollableRulePanel.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollableRulePanel.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollableRulePanel.verticalScrollBar.unitIncrement = 10
        scrollableRulePanel.isOpaque = false
        scrollableRulePanel.viewport.isOpaque = false
        add(scrollableRulePanel, BorderLayout.CENTER)
    }


    fun clear() {
        editor.text = ""
    }

    fun updateHtml(ruleDescription: RuleDescription) {
        ruleDescriptionHyperLinkListener.setRuleKey(ruleDescription.key)
        SwingHelper.setHtml(editor, ruleDescription.html, UIUtil.getLabelForeground())
    }


    private class RuleDescriptionHyperLinkListener(private val project: Project) : BrowserHyperlinkListener() {
        private var ruleKey: String? = null
        fun setRuleKey(ruleKey: String?) {
            this.ruleKey = ruleKey
        }

        public override fun hyperlinkActivated(e: HyperlinkEvent) {
            if (e.description.startsWith("#rule")) {
                openRuleSettings(ruleKey)
                return
            }
            super.hyperlinkActivated(e)
        }

        private fun openRuleSettings(ruleKey: String?) {
            if (ruleKey != null) {
                val configurable = SonarLintGlobalConfigurable()
                ShowSettingsUtil.getInstance().editConfigurable(project, configurable) { configurable.selectRule(ruleKey) }
            }
        }
    }

}