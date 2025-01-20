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
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.psi.XmlElementFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import org.apache.commons.text.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleFragment
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleType
import org.sonarlint.intellij.ui.ruledescription.section.HtmlFragment
import org.sonarlint.intellij.ui.ruledescription.section.Section

class RuleParsingUtils {

    companion object {
        private const val PRE_TAG_ENDING = "</pre>"

        fun parseCodeExamples(project: Project, parent: Disposable, htmlDescription: String, fileType: FileType): JScrollPane {
            val mainPanel = JBPanel<JBPanel<*>>(VerticalFlowLayout(0, 0))
            var remainingRuleDescription = htmlDescription
            var computedRuleDescription = ""
            var matcherStart: Matcher = Pattern.compile("<pre[^>]*>").matcher(remainingRuleDescription)
            var matcherEnd: Matcher = Pattern.compile(PRE_TAG_ENDING).matcher(remainingRuleDescription)

            val section = Section()
            val xmlElementFactory = XmlElementFactory.getInstance(project)
            while (matcherStart.find() && matcherEnd.find()) {
                val front: String = remainingRuleDescription.substring(0, matcherStart.start()).trim()

                if (front.isNotBlank()) {
                    section.mergeOrAdd(HtmlFragment(front))
                }
                computedRuleDescription += front

                val preTag =
                    xmlElementFactory.createTagFromText(
                        remainingRuleDescription.substring(matcherStart.start(), matcherStart.end()).trim() + PRE_TAG_ENDING
                    )
                val diffId = preTag.getAttributeValue("data-diff-id")
                val diffType = preTag.getAttributeValue("data-diff-type")?.let { CodeExampleType.from(it) }

                val middle: String = remainingRuleDescription.substring(matcherStart.end(), matcherEnd.start()).trim()

                if (middle.isNotBlank()) {
                    if (isWithinTable(computedRuleDescription)) {
                        section.mergeOrAdd(HtmlFragment("<pre>$middle$PRE_TAG_ENDING"))
                    } else {
                        section.add(CodeExampleFragment(replaceSpaceCharacters(middle), diffType, diffId))
                    }
                }
                computedRuleDescription += remainingRuleDescription.substring(matcherStart.start(), matcherEnd.end())
                remainingRuleDescription = remainingRuleDescription.substring(matcherEnd.end(), remainingRuleDescription.length).trim()
                matcherStart = Pattern.compile("<pre[^>]*>").matcher(remainingRuleDescription)
                matcherEnd = Pattern.compile(PRE_TAG_ENDING).matcher(remainingRuleDescription)
            }

            if (remainingRuleDescription.isNotBlank()) {
                section.mergeOrAdd(HtmlFragment(remainingRuleDescription))
            }

            transformAndAddSections(section, project, parent, fileType, mainPanel)

            return createScrollPane(mainPanel)
        }

        private fun transformAndAddSections(section: Section, project: Project, parent: Disposable, fileType: FileType, mainPanel: JBPanel<*>) {
            section.fragments.map {
                when (it) {
                    is HtmlFragment -> RuleHtmlViewer(false).apply { updateHtml(it.html) }
                    is CodeExampleFragment -> RuleCodeSnippet(project, fileType, it).apply {
                        Disposer.tryRegister(parent, this)
                    }
                }
            }.forEach { mainPanel.add(it) }
        }

        private fun isWithinTable(previousHtml: String): Boolean {
            // very naive implementation, but should be good enough
            return StringUtils.countMatches(previousHtml, "<table>") > StringUtils.countMatches(previousHtml, "</table>")
        }

        private fun replaceSpaceCharacters(text: String): String {
            return StringEscapeUtils.unescapeHtml4(text)
                // &nbsp;
                .replace("\u00a0","")
                // &ensp;
                .replace("\u2002","")
                // &emsp;
                .replace("\u2003","")
                // &thinsp;
                .replace("\u2009","")
        }

        private fun createScrollPane(mainPanel: JBPanel<*>): JScrollPane {
            return ScrollPaneFactory.createScrollPane(mainPanel).apply {
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                verticalScrollBar.unitIncrement = 10
                isOpaque = false
                viewport.isOpaque = false
                border = JBUI.Borders.empty()
            }
        }
    }

}
