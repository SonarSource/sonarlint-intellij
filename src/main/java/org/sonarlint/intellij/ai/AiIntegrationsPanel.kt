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
package org.sonarlint.intellij.ai

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationCheckStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliIntegrationRecordingStatus
import org.sonarlint.intellij.documentation.SonarLintDocumentation

private const val PAGE_TITLE = "Bring SonarQube to your AI agents"
private const val PAGE_DESCRIPTION = "Find and fix issues as your agent writes code."
private const val CLI_CARD_TITLE = "SonarQube CLI"
private const val CLI_CARD_HEADLINE = "Catch issues as AI writes code"
private val CLI_CARD_DESCRIPTION_HTML =
    "Give your agents local code analysis with <a href=\"${SonarLintDocumentation.Intellij.SONAR_VORTEX_LINK}\">Sonar Vortex&nbsp;<icon src=\"ide/external_link_arrow.svg\"></a> to find bugs and vulnerabilities before you commit."
private const val MCP_CARD_TITLE = "SonarQube MCP Server"
private const val MCP_CARD_HEADLINE = "Bring project context to your agent"
private const val MCP_CARD_DESCRIPTION = "Ask your agent about issues, quality gates, and coverage in your SonarQube projects."
private const val REFRESH_LABEL = "Refresh"
private const val CHECKING_STATUS = "Checking"
private const val NEEDS_ATTENTION = "Needs attention"
private const val NOT_DETECTED_STATUS = "Not detected"
private const val UNAVAILABLE_STATUS = "Unavailable"
private const val INSTALLED_STATUS = "Installed"
private const val CLI_LOADING_MESSAGE = "Discovering your CLI and coding agents…"
private const val MCP_LOADING_MESSAGE = "Checking which agents support MCP…"
private const val MCP_ERROR_MESSAGE = "MCP capabilities could not be loaded."
private const val NO_AGENTS_MESSAGE = "No supported AI agents detected."
private const val RETRY_LABEL = "Try again"
private const val MANAGE_AGENTS_LABEL = "Manage agents"
private const val SUPPORTED_STATUS = "Supported"
private const val NOT_SUPPORTED_STATUS = "Not supported"
private const val CLI_GUIDE_LABEL = "SonarQube CLI guide"
private const val MCP_GUIDE_LABEL = "MCP configuration guide"
private const val CONFIGURATION_DETAILS_LABEL = "Configuration details"

class AiIntegrationsPanel(
    private val registry: AiAgentRegistry = AiAgentRegistry(),
    private val openLink: (String) -> Unit = { BrowserUtil.browse(it) }
) : JBPanel<AiIntegrationsPanel>(BorderLayout()), Disposable {
    companion object {
        internal const val WIDE_LAYOUT_THRESHOLD = 900
        private const val NARROW_CONTENT_WIDTH = 760
        private const val WIDE_CONTENT_WIDTH = 1160

        private val CARD_BACKGROUND = JBColor(Color(0xFFFFFF), Color(0x2B2D30))
        private val CARD_BORDER = JBColor(Color(0xDDE0E5), Color(0x45474D))
        private val ROW_BACKGROUND = JBColor(Color(0xF7F8FA), Color(0x323438))
        private val ROW_BORDER = JBColor(Color(0xE9EBEF), Color(0x3D3F44))
        private val SECONDARY_TEXT = JBColor(Color(0x5F6673), Color(0xA8ADBD))
        private val LINK_TEXT = JBColor(Color(0x0B6BCB), Color(0x6CAEFF))
    }

    private val cards = ContentColumn()
    private val disposed = AtomicBoolean()
    private var refreshListener: () -> Unit = {}
    private var intentListener: (AiIntegrationsIntent) -> Unit = {}
    private var currentState: AiIntegrationsPanelState = AiIntegrationsPanelState.Loading
    private var wide = false
    private var cliDetailsExpanded = false
    private var mcpDetailsExpanded = false
    private val expandedCliConfigurations = mutableSetOf<AiAgent>()

    val isDisposed: Boolean
        get() = disposed.get()

    init {
        isOpaque = false
        val scrollContent = WidthTrackingScrollContent().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(14, 20, 28, 20)
            add(cards)
            add(Box.createHorizontalGlue())
        }
        val scrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(scrollContent, true)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = JBUI.Borders.empty()
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
        add(scrollPane, BorderLayout.CENTER)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                updateResponsiveLayout()
            }
        })
        render(AiIntegrationsPanelState.Loading)
    }

    fun setRefreshListener(listener: () -> Unit) {
        refreshListener = listener
    }

    fun setIntentListener(listener: (AiIntegrationsIntent) -> Unit) {
        intentListener = listener
    }

    fun render(state: AiIntegrationsPanelState) {
        if (isDisposed) {
            return
        }
        currentState = state
        rebuild()
    }

    private fun rebuild() {
        cards.removeAll()
        cards.contentWidth = if (wide) WIDE_CONTENT_WIDTH else NARROW_CONTENT_WIDTH
        cards.add(createPageHeader())
        cards.add(verticalSpace(20))
        cards.add(createIntegrationCards(createCliCard(currentState), createMcpCard(currentState)))
        cards.add(verticalSpace(12))
        cards.add(createPageFooter())
        cards.revalidate()
        cards.repaint()
    }

    private fun updateResponsiveLayout() {
        val newWide = width >= JBUI.scale(WIDE_LAYOUT_THRESHOLD)
        if (wide != newWide) {
            wide = newWide
            rebuild()
        }
    }

    private fun createIntegrationCards(cliCard: JPanel, mcpCard: JPanel): JPanel = NaturalHeightPanel().apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        if (wide) {
            layout = GridLayout(1, 2, JBUI.scale(14), 0)
            add(cliCard)
            add(mcpCard)
        } else {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(cliCard)
            add(verticalSpace(14))
            add(mcpCard)
        }
    }

    private fun createPageHeader(): JPanel = JBPanel<JBPanel<*>>().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        add(bodyText(PAGE_TITLE).apply {
            font = JBFont.label().asBold().biggerOn(7.0f)
        })
        add(verticalSpace(7))
        add(bodyText(PAGE_DESCRIPTION, secondary = true))
    }

    private fun createCliCard(state: AiIntegrationsPanelState): JPanel = createCard(
        CLI_CARD_TITLE,
        CLI_CARD_HEADLINE,
        createCliDescription(),
        cliStatus(state)
    ) {
        when (state) {
            AiIntegrationsPanelState.Loading -> addMessage(CLI_LOADING_MESSAGE)
            is AiIntegrationsPanelState.Error -> {
                addMessage(state.message)
                addPrimaryAction(RETRY_LABEL)
            }
            is AiIntegrationsPanelState.Ready -> addCliOverview(state.snapshot)
        }
        addDocumentationLink(CLI_GUIDE_LABEL, SonarLintDocumentation.Intellij.SONARQUBE_CLI_GUIDE_LINK)
    }

    private fun createMcpCard(state: AiIntegrationsPanelState): JPanel = createCard(
        MCP_CARD_TITLE,
        MCP_CARD_HEADLINE,
        bodyText(MCP_CARD_DESCRIPTION, secondary = true),
        mcpStatus(state)
    ) {
        when (state) {
            AiIntegrationsPanelState.Loading -> addMessage(MCP_LOADING_MESSAGE)
            is AiIntegrationsPanelState.Error -> addMessage(MCP_ERROR_MESSAGE)
            is AiIntegrationsPanelState.Ready -> addMcpConfigurations(state.snapshot)
        }
        addDocumentationLink(MCP_GUIDE_LABEL, SonarLintDocumentation.Intellij.MCP_CONFIGURATION_GUIDE_LINK)
    }

    private fun createCliDescription() = SwingHelper.createHtmlViewer(false, JBFont.label(), null, SECONDARY_TEXT).apply {
        text = CLI_CARD_DESCRIPTION_HTML
        border = JBUI.Borders.empty()
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                event.description?.let(openLink)
            }
        }
    }

    private fun mcpStatus(state: AiIntegrationsPanelState): CardStatus = when (state) {
        AiIntegrationsPanelState.Loading -> CardStatus(CHECKING_STATUS, StatusTone.NEUTRAL)
        is AiIntegrationsPanelState.Error -> CardStatus(NEEDS_ATTENTION, StatusTone.WARNING)
        is AiIntegrationsPanelState.Ready -> mcpStatus(state.snapshot)
    }

    private fun mcpStatus(snapshot: AiIntegrationSnapshot): CardStatus {
        val configurations = snapshot.mcpConfigurations.values
        val needsAttention = configurations.any {
            it.state == McpConfigurationKind.UNKNOWN || it.state == McpConfigurationKind.MALFORMED
        }
        return CardStatus("${configurations.size} supported", if (needsAttention) StatusTone.WARNING else StatusTone.NEUTRAL)
    }

    private fun CardBuilder.addMcpConfigurations(snapshot: AiIntegrationSnapshot) {
        val configurations = snapshot.mcpConfigurations.values
        addMetadata(listOf(agentCountText(snapshot.agents.size)))
        if (configurations.isNotEmpty()) {
            val configuredCount = configurations.count {
                it.state == McpConfigurationKind.STANDALONE || it.state == McpConfigurationKind.CLI_MANAGED
            }
            val attentionCount = configurations.count {
                it.state == McpConfigurationKind.UNKNOWN || it.state == McpConfigurationKind.MALFORMED
            }
            addMetadata(listOf(mcpSummary(configuredCount, configurations.size, attentionCount)))
        }

        val details = JBPanel<JBPanel<*>>().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        fun updateDetails(show: Boolean) {
            mcpDetailsExpanded = show
            details.removeAll()
            if (show) {
                val detailBuilder = CardBuilder(details)
                if (configurations.isEmpty()) {
                    detailBuilder.addMessage("No standalone MCP setup is available for the detected agents.")
                } else {
                    configurations.forEach { detailBuilder.addMcpConfigurationRow(it) }
                }
            }
            details.isVisible = show
            cards.revalidate()
            cards.repaint()
        }
        addDisclosure(mcpDetailsExpanded, ::updateDetails)
        panel.add(details)
        updateDetails(mcpDetailsExpanded)
    }

    private fun CardBuilder.addMcpConfigurationRow(configuration: McpAgentConfiguration) {
        val actions = when (configuration.state) {
            McpConfigurationKind.NOT_CONFIGURED -> listOf(
                RowAction("Set up", AiIntegrationsIntent.SetUpMcp(configuration.agent))
            )
            McpConfigurationKind.STANDALONE,
            McpConfigurationKind.CLI_MANAGED,
            McpConfigurationKind.UNKNOWN,
            McpConfigurationKind.MALFORMED -> listOf(
                RowAction("Open", AiIntegrationsIntent.OpenMcpConfiguration(configuration.agent))
            )
        }
        addAgentRow(
            registry.displayName(configuration.agent),
            configuration.displayText(),
            diagnostic = configuration.diagnostics.takeIf { configuration.state != McpConfigurationKind.CLI_MANAGED && it.isNotEmpty() }?.joinToString(" • "),
            actions = actions
        )
    }

    private fun CardBuilder.addCliOverview(snapshot: AiIntegrationSnapshot) {
        val cli = snapshot.cli
        val metadata = buildList {
            if (cli.installation == CliInstallationStatus.INSTALLED) {
                add(if (cli.authentication == CliAuthenticationStatus.AUTHENTICATED && !cli.organization.isNullOrBlank()) {
                    "Authenticated with ${cli.organization}"
                } else {
                    cli.authentication.displayText()
                })
            }
            cli.version?.let { add("v$it") }
        }
        if (metadata.isNotEmpty()) {
            addMetadata(metadata)
        }
        addCliPrimaryAction(snapshot)
        addCapabilityOverview(
            snapshot.agents,
            { it.cliIntegrationSupported },
            cliDetailsExpanded,
            snapshot.cliIntegrations,
            rowActions = { capability ->
                if (cli.authentication == CliAuthenticationStatus.AUTHENTICATED && capability.cliIntegrationSupported) {
                    listOf(RowAction("Integrate", AiIntegrationsIntent.IntegrateCli(capability.agent)))
                } else {
                    emptyList()
                }
            }
        ) { cliDetailsExpanded = it }
    }

    private fun CardBuilder.addCliPrimaryAction(snapshot: AiIntegrationSnapshot) {
        when (snapshot.cli.installation) {
            CliInstallationStatus.NOT_INSTALLED -> addPrimaryAction("Install SonarQube CLI", AiIntegrationsIntent.InstallCli)
            CliInstallationStatus.UNUSABLE -> addPrimaryAction("Troubleshoot") {
                openLink(SonarLintDocumentation.Intellij.SONARQUBE_CLI_GUIDE_LINK)
            }
            CliInstallationStatus.INSTALLED -> when (snapshot.cli.authentication) {
                CliAuthenticationStatus.UNAUTHENTICATED,
                CliAuthenticationStatus.INVALID,
                CliAuthenticationStatus.UNVERIFIED -> addPrimaryAction("Sign in", AiIntegrationsIntent.AuthenticateCli)
                CliAuthenticationStatus.UNAVAILABLE,
                CliAuthenticationStatus.UNKNOWN -> addPrimaryAction("Check again")
                CliAuthenticationStatus.AUTHENTICATED -> Unit
            }
        }
    }

    private fun CardBuilder.addCapabilityOverview(
        capabilities: List<AgentCapability>,
        supported: (AgentCapability) -> Boolean,
        expanded: Boolean,
        cliIntegrations: List<AgentCliIntegration>? = null,
        rowActions: (AgentCapability) -> List<RowAction> = { emptyList() },
        onExpandedChange: (Boolean) -> Unit
    ) {
        addMetadata(listOf(agentCountText(capabilities.size)))
        val details = JBPanel<JBPanel<*>>().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        fun updateDetails(show: Boolean) {
            details.removeAll()
            if (show) {
                val detailBuilder = CardBuilder(details)
                if (capabilities.isEmpty()) {
                    detailBuilder.addMessage(NO_AGENTS_MESSAGE)
                }
                capabilities.forEach { capability ->
                    val integration = if (cliIntegrations != null && supported(capability)) {
                        cliIntegrations.firstOrNull { it.agent == capability.agent }
                            ?: AgentCliIntegration(capability.agent, CliIntegrationRecordingStatus.UNKNOWN, emptyList())
                    } else {
                        null
                    }
                    detailBuilder.addAgentRow(
                        registry.displayName(capability.agent),
                        if (supported(capability)) SUPPORTED_STATUS else NOT_SUPPORTED_STATUS,
                        integration,
                        actions = rowActions(capability)
                    )
                }
            }
            details.isVisible = show
            cards.revalidate()
            cards.repaint()
        }
        addDisclosure(expanded) { show ->
            onExpandedChange(show)
            updateDetails(show)
        }
        panel.add(details)
        updateDetails(expanded)
    }

    private fun createCard(
        title: String,
        headline: String,
        description: JComponent,
        status: CardStatus,
        content: CardBuilder.() -> Unit
    ): JPanel {
        val body = RoundedSurfacePanel(CARD_BACKGROUND, CARD_BORDER, 16).apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(18, 20, 12, 20)
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        val content = JBPanel<JBPanel<*>>().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        content.add(createCardHeader(title, headline, description, status))
        content.add(verticalSpace(16))
        CardBuilder(content).content()
        body.add(content, BorderLayout.NORTH)
        return body
    }

    private fun createCardHeader(
        title: String,
        headline: String,
        description: JComponent,
        status: CardStatus
    ): JPanel = JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(12), 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(JBPanel<JBPanel<*>>().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JBLabel(title).apply {
                font = JBFont.label().asBold()
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(verticalSpace(9))
            add(bodyText(headline).apply { font = JBFont.label().asBold().biggerOn(2.0f) })
            add(verticalSpace(6))
            add(description)
        }, BorderLayout.CENTER)
        add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            add(StatusPill(status), BorderLayout.NORTH)
        }, BorderLayout.EAST)
    }

    private fun createPageFooter(): JPanel = JBPanel<JBPanel<*>>().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(36))
        add(createSecondaryButton(REFRESH_LABEL, AllIcons.Actions.Refresh))
        add(Box.createHorizontalGlue())
    }

    private inner class CardBuilder(val panel: JPanel) {
        fun addMessage(text: String) {
            panel.add(bodyText(text))
            panel.add(verticalSpace(10))
        }

        fun addMetadata(values: List<String>) {
            panel.add(JBLabel(values.joinToString("  •  ")).apply {
                foreground = SECONDARY_TEXT
                font = JBFont.small()
                alignmentX = Component.LEFT_ALIGNMENT
            })
            panel.add(verticalSpace(10))
        }

        fun addDisclosure(expanded: Boolean, toggle: (Boolean) -> Unit) {
            panel.add(createDisclosureButton(expanded, toggle = toggle))
            panel.add(verticalSpace(8))
        }

        fun addDocumentationLink(label: String, url: String) {
            panel.add(createLinkButton(label, url))
        }

        fun addAgentRow(name: String, status: String, integration: AgentCliIntegration? = null, diagnostic: String? = null, actions: List<RowAction> = emptyList()) {
            val row = RoundedSurfacePanel(ROW_BACKGROUND, ROW_BORDER, 10, naturalHeight = integration != null).apply {
                layout = BorderLayout(JBUI.scale(12), 0)
                border = JBUI.Borders.empty(9, 11)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            row.add(JBPanel<JBPanel<*>>().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JBLabel(name).apply {
                    font = JBFont.label().asBold()
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                add(JBLabel(status).apply {
                    foreground = SECONDARY_TEXT
                    font = JBFont.small()
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                diagnostic?.let { add(bodyText(it)) }
            }, BorderLayout.CENTER)
            if (integration != null || actions.isNotEmpty()) {
                row.add(JBPanel<JBPanel<*>>().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    integration?.let { add(StatusPill(it.recordingStatus.toStatus())) }
                    if (actions.isNotEmpty()) {
                        add(JBPanel<JBPanel<*>>().apply {
                            isOpaque = false
                            layout = BoxLayout(this, BoxLayout.X_AXIS)
                            actions.forEachIndexed { index, action ->
                                if (index > 0) {
                                    add(horizontalSpace(4))
                                }
                                add(createCompactButton(action.label, action.intent))
                            }
                        })
                    }
                }, BorderLayout.EAST)
                if (integration != null && integration.configurations.isNotEmpty()) {
                    addCliConfigurationDetails(row, integration)
                }
            }
            panel.add(row)
            panel.add(verticalSpace(6))
        }

        private fun addCliConfigurationDetails(row: JPanel, integration: AgentCliIntegration) {
            val details = JBPanel<JBPanel<*>>().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            fun updateDetails(show: Boolean) {
                details.removeAll()
                if (show) {
                    val builder = CardBuilder(details)
                    integration.configurations.forEach { configuration ->
                        builder.addMessage(configuration.path?.takeIf { it.isNotBlank() } ?: "Configuration path not reported")
                        configuration.mcp?.let { builder.addMetadata(listOf("MCP: ${it.displayText()}")) }
                        configuration.hooks?.let { builder.addMetadata(listOf("Hooks: ${it.displayText()}")) }
                    }
                }
                details.isVisible = show
                cards.revalidate()
                cards.repaint()
            }
            val expanded = integration.agent in expandedCliConfigurations
            row.add(JBPanel<JBPanel<*>>().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(verticalSpace(8))
                add(createDisclosureButton(expanded, CONFIGURATION_DETAILS_LABEL) { show ->
                    if (show) {
                        expandedCliConfigurations.add(integration.agent)
                    } else {
                        expandedCliConfigurations.remove(integration.agent)
                    }
                    updateDetails(show)
                })
                add(verticalSpace(6))
                add(details)
            }, BorderLayout.SOUTH)
            updateDetails(expanded)
        }

        fun addPrimaryAction(label: String) {
            addPrimaryAction(label) { refreshListener() }
        }

        fun addPrimaryAction(label: String, intent: AiIntegrationsIntent) {
            addPrimaryAction(label) { dispatchIntent(intent) }
        }

        fun addPrimaryAction(label: String, action: () -> Unit) {
            panel.add(createPrimaryButton(label, action).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })
            panel.add(verticalSpace(6))
        }
    }

    private fun createPrimaryButton(label: String, action: () -> Unit): JButton = JButton(label).apply {
        addActionListener { action() }
    }

    private fun createSecondaryButton(label: String, icon: Icon): JButton = JButton(label, icon).apply {
        addActionListener { refreshListener() }
    }

    private fun createCompactButton(label: String, intent: AiIntegrationsIntent): JButton = JButton(label).apply {
        isOpaque = false
        addActionListener { dispatchIntent(intent) }
    }

    private fun createDisclosureButton(expanded: Boolean, label: String = MANAGE_AGENTS_LABEL, toggle: (Boolean) -> Unit): JToggleButton =
        JToggleButton(disclosureLabel(expanded, label), expanded).apply {
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            border = JBUI.Borders.empty()
            margin = JBUI.emptyInsets()
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = LINK_TEXT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                text = disclosureLabel(isSelected, label)
                toggle(isSelected)
            }
        }

    private fun createLinkButton(label: String, url: String): JButton = JButton(label, AllIcons.Ide.External_link_arrow).apply {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        border = JBUI.Borders.empty()
        margin = JBUI.emptyInsets()
        horizontalAlignment = SwingConstants.LEFT
        horizontalTextPosition = SwingConstants.LEFT
        iconTextGap = JBUI.scale(4)
        alignmentX = Component.LEFT_ALIGNMENT
        foreground = LINK_TEXT
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { openLink(url) }
    }

    private fun dispatchIntent(intent: AiIntegrationsIntent) {
        intentListener(intent)
    }

    override fun dispose() {
        disposed.set(true)
        refreshListener = {}
        intentListener = {}
    }

    internal data class RowAction(val label: String, val intent: AiIntegrationsIntent)

    private class ContentColumn : JBPanel<ContentColumn>() {
        var contentWidth = NARROW_CONTENT_WIDTH

        init {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        override fun getPreferredSize(): Dimension {
            val preferred = super.getPreferredSize()
            return Dimension(JBUI.scale(contentWidth), preferred.height)
        }

        override fun getMaximumSize(): Dimension = Dimension(JBUI.scale(contentWidth), Int.MAX_VALUE)

        override fun getMinimumSize(): Dimension = Dimension(0, 0)
    }

    private class WidthTrackingScrollContent : JBPanel<WidthTrackingScrollContent>(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)

        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
            (visibleRect.height - JBUI.scale(16)).coerceAtLeast(JBUI.scale(16))

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false
    }

    private class NaturalHeightPanel : JBPanel<NaturalHeightPanel>() {
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}

private data class CardStatus(val text: String, val tone: StatusTone)

private enum class StatusTone {
    SUCCESS,
    WARNING,
    NEUTRAL
}

private class StatusPill(status: CardStatus) : JBPanel<StatusPill>() {
    init {
        isOpaque = false
        layout = BorderLayout()
        border = JBUI.Borders.empty(4, 9)
        val (foregroundColor, backgroundColor) = when (status.tone) {
            StatusTone.SUCCESS -> SUCCESS_TEXT to SUCCESS_BACKGROUND
            StatusTone.WARNING -> WARNING_TEXT to WARNING_BACKGROUND
            StatusTone.NEUTRAL -> NEUTRAL_TEXT to NEUTRAL_BACKGROUND
        }
        background = backgroundColor
        add(JBLabel(status.text).apply {
            foreground = foregroundColor
            font = JBFont.small().asBold()
        })
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2d = graphics.create() as Graphics2D
        graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2d.color = background
        graphics2d.fillRoundRect(0, 0, width, height, JBUI.scale(16), JBUI.scale(16))
        graphics2d.dispose()
        super.paintComponent(graphics)
    }

    companion object {
        private val SUCCESS_TEXT = JBColor(Color(0x176B3A), Color(0x82D6A2))
        private val SUCCESS_BACKGROUND = JBColor(Color(0xE7F6EC), Color(0x243C2D))
        private val WARNING_TEXT = JBColor(Color(0x8A4B08), Color(0xF2B66D))
        private val WARNING_BACKGROUND = JBColor(Color(0xFFF2DF), Color(0x45331F))
        private val NEUTRAL_TEXT = JBColor(Color(0x545B66), Color(0xBEC3CF))
        private val NEUTRAL_BACKGROUND = JBColor(Color(0xEFF1F4), Color(0x393B40))
    }
}

private class RoundedSurfacePanel(
    private val fillColor: Color,
    private val strokeColor: Color,
    private val radius: Int,
    private val naturalHeight: Boolean = false
) : JBPanel<RoundedSurfacePanel>() {
    init {
        isOpaque = false
    }

    override fun getMaximumSize(): Dimension =
        if (naturalHeight) Dimension(Int.MAX_VALUE, preferredSize.height) else super.getMaximumSize()

    override fun paintComponent(graphics: Graphics) {
        val graphics2d = graphics.create() as Graphics2D
        graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2d.color = fillColor
        graphics2d.fillRoundRect(0, 0, width - 1, height - 1, JBUI.scale(radius), JBUI.scale(radius))
        graphics2d.color = strokeColor
        graphics2d.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(radius), JBUI.scale(radius))
        graphics2d.dispose()
        super.paintComponent(graphics)
    }
}

private fun AiIntegrationsPanel.cliStatus(state: AiIntegrationsPanelState): CardStatus = when (state) {
    AiIntegrationsPanelState.Loading -> CardStatus(CHECKING_STATUS, StatusTone.NEUTRAL)
    is AiIntegrationsPanelState.Error -> CardStatus(NEEDS_ATTENTION, StatusTone.WARNING)
    is AiIntegrationsPanelState.Ready -> state.snapshot.cli.toStatus()
}

private fun CliState.toStatus(): CardStatus = when (installation) {
    CliInstallationStatus.NOT_INSTALLED -> CardStatus(NOT_DETECTED_STATUS, StatusTone.WARNING)
    CliInstallationStatus.UNUSABLE -> CardStatus(UNAVAILABLE_STATUS, StatusTone.WARNING)
    CliInstallationStatus.INSTALLED -> CardStatus(INSTALLED_STATUS, StatusTone.SUCCESS)
}

private fun CliIntegrationRecordingStatus.toStatus(): CardStatus = when (this) {
    CliIntegrationRecordingStatus.RECORDED -> CardStatus("Integration recorded", StatusTone.SUCCESS)
    CliIntegrationRecordingStatus.NOT_RECORDED -> CardStatus("No integration recorded", StatusTone.NEUTRAL)
    CliIntegrationRecordingStatus.UNKNOWN -> CardStatus("Unknown", StatusTone.NEUTRAL)
}

private fun CliIntegrationCheckStatus.displayText(): String = when (this) {
    CliIntegrationCheckStatus.CONFIGURED -> "Configured"
    CliIntegrationCheckStatus.NOT_CONFIGURED -> "Not configured"
    CliIntegrationCheckStatus.INVALID -> "Invalid configuration"
    CliIntegrationCheckStatus.UNKNOWN -> "Unknown"
}

private fun disclosureLabel(expanded: Boolean, label: String): String = "${if (expanded) "▾" else "▸"} $label"

private fun agentCountText(count: Int): String = "$count ${if (count == 1) "agent" else "agents"} detected"

private fun mcpSummary(configuredCount: Int, totalCount: Int, attentionCount: Int): String = when {
    attentionCount > 0 -> "$attentionCount ${if (attentionCount == 1) "configuration needs" else "configurations need"} attention."
    configuredCount == totalCount -> "MCP is configured for all $totalCount detected ${if (totalCount == 1) "agent" else "agents"}."
    configuredCount > 0 -> "$configuredCount of $totalCount detected agents are configured."
    else -> "MCP is ready to set up for $totalCount detected ${if (totalCount == 1) "agent" else "agents"}."
}

private fun McpAgentConfiguration.displayText(): String = when (state) {
    McpConfigurationKind.NOT_CONFIGURED -> "Not configured"
    McpConfigurationKind.STANDALONE -> "Configured"
    McpConfigurationKind.CLI_MANAGED -> "Managed by CLI"
    McpConfigurationKind.UNKNOWN -> "Status unknown"
    McpConfigurationKind.MALFORMED -> "Invalid configuration"
}

private fun bodyText(text: String, secondary: Boolean = false): JBTextArea = JBTextArea(text).apply {
    font = JBFont.label()
    lineWrap = true
    wrapStyleWord = true
    isEditable = false
    isFocusable = false
    isOpaque = false
    border = JBUI.Borders.empty()
    alignmentX = Component.LEFT_ALIGNMENT
    if (secondary) {
        foreground = JBColor(Color(0x5F6673), Color(0xA8ADBD))
    }
}

private fun verticalSpace(size: Int): Component = Box.createRigidArea(Dimension(0, JBUI.scale(size)))

private fun horizontalSpace(size: Int): Component = Box.createRigidArea(Dimension(JBUI.scale(size), 0))

private fun CliAuthenticationStatus.displayText() = when (this) {
    CliAuthenticationStatus.AUTHENTICATED -> "Authenticated"
    CliAuthenticationStatus.UNAUTHENTICATED -> "Not signed in"
    CliAuthenticationStatus.INVALID -> "Credentials invalid"
    CliAuthenticationStatus.UNVERIFIED -> "Connection not verified"
    CliAuthenticationStatus.UNAVAILABLE -> "Unavailable"
    CliAuthenticationStatus.UNKNOWN -> "Status unknown"
}
