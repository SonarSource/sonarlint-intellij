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
package org.sonarlint.intellij.ui.grip

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import javax.swing.JButton
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import org.jdesktop.swingx.HorizontalLayout
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.FeedbackRating

class FeedbackPanel(private val ruleMessage: String) : JBPanel<FeedbackPanel>(VerticalFlowLayout(5)) {

    private val ruleMessageLabel = JBLabel("Rule message: $ruleMessage").apply {
        font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        horizontalAlignment = SwingConstants.CENTER
    }
    private val descriptionLabel = JBLabel(
        "<html><center>Do not hesitate to provide feedback on the quality of the suggestion.<br>" +
            "For example, if the suggestion is not relevant, please let us know why.<br>" +
            "Your feedback will help us improve the quality of the suggestions!</center></html>"
    ).apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
        horizontalAlignment = SwingConstants.CENTER
    }
    private val feedbackBadButton = JButton("BAD").apply {
        foreground = JBColor.RED
    }
    private val feedbackOkButton = JButton("ACCEPTABLE").apply {
        foreground = JBColor.ORANGE
    }
    private val feedbackGoodButton = JButton("VERY GOOD").apply {
        foreground = JBColor.GREEN
    }
    private val feedbackComment = JBTextArea().apply {
        emptyText.setText("How was the quality of the suggestion?")
        rows = 5
    }
    private var selectedButton: JButton? = null

    init {
        feedbackBadButton.addActionListener {
            selectedButton = feedbackBadButton
            feedbackBadButton.background = JBColor.BLUE
            feedbackOkButton.background = null
            feedbackGoodButton.background = null
        }

        feedbackOkButton.addActionListener {
            selectedButton = feedbackOkButton
            feedbackBadButton.background = null
            feedbackOkButton.background = JBColor.BLUE
            feedbackGoodButton.background = null
        }

        feedbackGoodButton.addActionListener {
            selectedButton = feedbackGoodButton
            feedbackBadButton.background = null
            feedbackOkButton.background = null
            feedbackGoodButton.background = JBColor.BLUE
        }

        add(ruleMessageLabel)
        add(descriptionLabel)
        add(JBPanel<JBPanel<*>>(HorizontalLayout(5)).apply {
            add(JBLabel("Rate the fix:"))
            add(feedbackBadButton)
            add(feedbackOkButton)
            add(feedbackGoodButton)
        })
        add(JBPanel<JBPanel<*>>(HorizontalLayout(5)).apply {
            add(JBLabel("Comment:"))
            add(JScrollPane(feedbackComment).apply {
                preferredSize = Dimension(600, preferredSize.height)
            })
        })
    }

    fun getStatus(): FeedbackRating? {
        return when (selectedButton) {
            feedbackBadButton -> return FeedbackRating.BAD
            feedbackOkButton -> return FeedbackRating.OK
            feedbackGoodButton -> return FeedbackRating.VERY_GOOD
            else -> null
        }
    }

    fun getComment(): String {
        return feedbackComment.text
    }

}
