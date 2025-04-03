package org.sonarlint.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
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
import org.sonarlint.intellij.telemetry.LinkTelemetry

data class HelpCard(
    val title: String,
    val linkText: String,
    val link: LinkTelemetry
)

private val DOCUMENTATION_CARD = HelpCard(
    "Want to know more about our product?",
    "Read the Documentation",
    LinkTelemetry.BASE_DOCS_HELP
)

private val COMMUNITY_CARD = HelpCard(
    "SonarQube for IDE support",
    "Get help in the Community Forum",
    LinkTelemetry.COMMUNITY_HELP
)

private val FEATURE_CARD = HelpCard(
    "Are you missing any feature?",
    "Go to Suggested Features",
    LinkTelemetry.SUGGEST_FEATURE_HELP
)

class SonarLintHelpAndSupportPanel(private val project: Project) : SimpleToolWindowPanel(false, false) {

    private val topLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val cardPanel = JBPanel<SonarLintHelpAndSupportPanel>()

    init {
        layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 10, 10, true, false)

        topLabel.apply {
            text = """
            Having issues with SonarQube for IDE? Open the <a href="#LogTab">Log tab</a>, 
            then <a href="#Verbose">enable the Analysis logs and Verbose output</a>.<br>
            Share your verbose logs with us in a post on the Community Forum. We are happy to help you debug!
        """.trimIndent()

            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    when (e.description) {
                        "#LogTab" -> getService(project, SonarLintToolWindow::class.java).openLogTab()
                        "#Verbose" -> LinkTelemetry.TROUBLESHOOTING_PAGE.browseWithTelemetry()
                    }
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
            add(ActionLink(card.linkText) { card.link.browseWithTelemetry() }.apply {
                setExternalLinkIcon()
            })
            maximumSize = Dimension(300, maximumSize.height)
        }
    }

}
