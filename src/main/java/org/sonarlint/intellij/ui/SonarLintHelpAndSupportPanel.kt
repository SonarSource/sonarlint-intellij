package org.sonarlint.intellij.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.SwingHelper
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.documentation.SonarLintDocumentation

data class HelpCard(
    val title: String,
    val linkText: String,
    val linkUrl: String
)

private val DOCUMENTATION_CARD = HelpCard(
    "Want to know more about our product?",
    "Read the Documentation",
    SonarLintDocumentation.Intellij.BASE_DOCS_URL
)

private val COMMUNITY_CARD = HelpCard(
    "SonarQube for IDE support",
    "Get help in the Community forum",
    SonarLintDocumentation.Community.COMMUNITY_LINK
)

private val FEATURE_CARD = HelpCard(
    "Are you missing any feature?",
    "Go to Suggested Features",
    SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK
)

class SonarLintHelpAndSupportPanel(private val toolWindow: ToolWindow, private val project: Project) : SimpleToolWindowPanel(false, false) {

    private val topLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val cardPanel = JBPanel<SonarLintHelpAndSupportPanel>()

    init {
        layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 30, 15, true, false)

        topLabel.apply {
            text = """
            Having issues with SonarQube for IntelliJ? Open the <a href="#LogTab">Log tab</a>, 
            then enable the <a href="${SonarLintDocumentation.Intellij.TROUBLESHOOTING_LINK}">Analysis logs and Verbose output</a>.<br>
            Share your verbose logs with us in a post on the Community Forum. We are happy to help you debug!
        """.trimIndent()

            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED && e.description == "#LogTab") {
                    getService(project, SonarLintToolWindow::class.java).openLogTab()
                }
            }
        }

        cardPanel.apply {
            layout = BoxLayout(cardPanel, BoxLayout.X_AXIS)
            add(generateHelpCard(DOCUMENTATION_CARD))
            add(Box.createHorizontalStrut(30))
            add(generateHelpCard(COMMUNITY_CARD))
            add(Box.createHorizontalStrut(30))
            add(generateHelpCard(FEATURE_CARD))
        }

        add(topLabel)
        add(JSeparator(SwingConstants.HORIZONTAL))
        add(cardPanel)
    }

    private fun generateHelpCard(card: HelpCard): JBPanel<SonarLintHelpAndSupportPanel> {
        return JBPanel<SonarLintHelpAndSupportPanel>(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false)).apply {
            add(JBLabel(card.title).apply {
                font = JBFont.label().asBold()
            })
            add(ActionLink(card.linkText) {
                BrowserUtil.browse(card.linkUrl)
            }.apply {
                setExternalLinkIcon()
            })
            maximumSize = Dimension(300, maximumSize.height)
        }
    }

}
