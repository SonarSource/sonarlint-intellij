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
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

class AiIntegrationsPanel(
    private val registry: AiAgentRegistry = AiAgentRegistry()
) : JBPanel<AiIntegrationsPanel>(BorderLayout()), Disposable {
    private val cards = ContentColumn()
    private val disposed = AtomicBoolean()
    private var intentListener: (AiIntegrationsIntent) -> Unit = {}
    private var currentState: AiIntegrationsPanelState = AiIntegrationsPanelState.Loading
    private var wide = false
    private var cliDetailsExpanded = false
    private var mcpDetailsExpanded = false
    private var integrationCards: JPanel? = null

    val isDisposed: Boolean
        get() = disposed.get()

    init {
        isOpaque = false
        val scrollContent = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(14, 20, 28, 20)
            add(cards)
            add(Box.createHorizontalGlue())
        }
        val scrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(scrollContent, true)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = BorderFactory.createEmptyBorder()
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

    internal fun renderedState(): AiIntegrationsPanelState = currentState

    internal fun isWideLayout(): Boolean = wide

    internal fun integrationCardsAreSideBySide(): Boolean = integrationCards?.layout is GridLayout

    internal fun integrationCardsUseNaturalHeight(): Boolean = integrationCards?.let {
        it.maximumSize.height == it.preferredSize.height
    } == true

    private fun rebuild() {
        cards.removeAll()
        cards.contentWidth = if (wide) WIDE_CONTENT_WIDTH else NARROW_CONTENT_WIDTH
        cards.add(createPageHeader())
        cards.add(verticalSpace(20))
        val newIntegrationCards = createIntegrationCards(createCliCard(currentState), createMcpCard(currentState))
        integrationCards = newIntegrationCards
        cards.add(newIntegrationCards)
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

    private fun createPageHeader(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(110))
        add(JBLabel("AI integrations").apply {
            font = JBFont.label().asBold().biggerOn(7.0f)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        add(verticalSpace(7))
        add(bodyText("Bring SonarQube quality and security insights into the AI tools you already use.", secondary = true))
    }

    private fun createCliCard(state: AiIntegrationsPanelState): JPanel = createCard(
        "SonarQube CLI",
        "Set up your coding agents once and keep them connected across projects.",
        cliStatus(state)
    ) {
        when (state) {
            AiIntegrationsPanelState.Loading -> addMessage("Discovering your CLI and coding agents…")
            AiIntegrationsPanelState.Remote -> addMessage(REMOTE_MESSAGE)
            is AiIntegrationsPanelState.Error -> {
                addMessage(state.message)
                addPrimaryAction("Try again", AiIntegrationsIntent.Refresh)
            }
            is AiIntegrationsPanelState.Empty -> addCliOverview(state.snapshot)
            is AiIntegrationsPanelState.Ready -> addCliOverview(state.snapshot)
        }
    }

    private fun createMcpCard(state: AiIntegrationsPanelState): JPanel = createCard(
        "SonarQube MCP Server",
        "Give each supported agent direct access to SonarQube context and tools.",
        mcpStatus(state)
    ) {
        when (state) {
            AiIntegrationsPanelState.Loading -> addMessage("Checking which agents support MCP…")
            AiIntegrationsPanelState.Remote -> addMessage(REMOTE_MESSAGE)
            is AiIntegrationsPanelState.Error -> addMessage("MCP capabilities could not be loaded.")
            is AiIntegrationsPanelState.Empty -> addMessage("Install a supported coding agent to configure MCP.")
            is AiIntegrationsPanelState.Ready -> addCapabilityOverview(
                state.snapshot.agents,
                { it.standaloneMcpSupported },
                mcpDetailsExpanded,
                "MCP"
            ) {
                mcpDetailsExpanded = !mcpDetailsExpanded
                rebuild()
            }
        }
    }

    private fun CardBuilder.addCliOverview(snapshot: AiIntegrationSnapshot) {
        val cli = snapshot.cli
        addMetadata(buildList {
            add(cli.installation.displayText())
            if (cli.installation == CliInstallationState.INSTALLED) {
                add(cli.authentication.displayText())
            }
            cli.version?.let { add("v$it") }
        })
        if (snapshot.agents.isEmpty()) {
            addMessage("No supported coding agents were detected.")
            return
        }
        addCapabilityOverview(
            snapshot.agents,
            { it.cliIntegrationSupported },
            cliDetailsExpanded,
            "CLI"
        ) {
            cliDetailsExpanded = !cliDetailsExpanded
            rebuild()
        }
    }

    private fun CardBuilder.addCapabilityOverview(
        capabilities: List<AgentCapability>,
        supported: (AgentCapability) -> Boolean,
        expanded: Boolean,
        integrationName: String,
        toggle: () -> Unit
    ) {
        val supportedCapabilities = capabilities.filter(supported)
        val agentNames = supportedCapabilities.map { registry.displayName(it.agent) }
        if (supportedCapabilities.isEmpty()) {
            addMessage("None of the detected agents support $integrationName integration yet.")
        } else {
            addMessage(agentSummary(agentNames, integrationName))
        }
        addDisclosure(if (expanded) "Hide agent details" else "View agent details", toggle)
        if (expanded) {
            capabilities.forEach { capability ->
                addAgentRow(
                    registry.displayName(capability.agent),
                    if (supported(capability)) "Available" else "Not available"
                )
            }
        }
    }

    private fun createCard(
        title: String,
        description: String,
        status: CardStatus,
        content: CardBuilder.() -> Unit
    ): JPanel {
        val body = RoundedSurfacePanel(CARD_BACKGROUND, CARD_BORDER, 16).apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(18, 20)
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        body.add(createCardHeader(title, description, status))
        body.add(verticalSpace(16))
        CardBuilder(body).content()
        return body
    }

    private fun createCardHeader(title: String, description: String, status: CardStatus): JPanel = JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(76))
        add(JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JBLabel(title).apply {
                font = JBFont.label().asBold().biggerOn(2.0f)
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(verticalSpace(4))
            add(bodyText(description, secondary = true))
        }, BorderLayout.CENTER)
        add(StatusPill(status), BorderLayout.EAST)
    }

    private fun createPageFooter(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(36))
        add(createSecondaryButton("Refresh", AllIcons.Actions.Refresh, AiIntegrationsIntent.Refresh))
        add(horizontalSpace(10))
        add(createLinkButton("Documentation", AiIntegrationsIntent.OpenDocumentation))
        add(Box.createHorizontalGlue())
    }

    private inner class CardBuilder(private val panel: JPanel) {
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

        fun addDisclosure(label: String, toggle: () -> Unit) {
            panel.add(createLinkButton(label, toggle))
            panel.add(verticalSpace(8))
        }

        fun addAgentRow(name: String, status: String, description: String? = null, actions: List<RowAction> = emptyList()) {
            val row = RoundedSurfacePanel(ROW_BACKGROUND, ROW_BORDER, 10).apply {
                layout = BorderLayout(JBUI.scale(12), 0)
                border = JBUI.Borders.empty(9, 11)
                alignmentX = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(if (description == null) 48 else 66))
            }
            row.add(JPanel().apply {
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
                description?.let {
                    add(JBLabel(it).apply {
                        foreground = SECONDARY_TEXT
                        font = JBFont.small()
                        alignmentX = Component.LEFT_ALIGNMENT
                    })
                }
            }, BorderLayout.CENTER)
            if (actions.isNotEmpty()) {
                row.add(JPanel().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    actions.forEachIndexed { index, action ->
                        if (index > 0) {
                            add(horizontalSpace(4))
                        }
                        add(createCompactButton(action.label, action.intent))
                    }
                }, BorderLayout.EAST)
            }
            panel.add(row)
            panel.add(verticalSpace(6))
        }

        fun addPrimaryAction(label: String, intent: AiIntegrationsIntent) {
            panel.add(createPrimaryButton(label, intent).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })
            panel.add(verticalSpace(6))
        }
    }

    private fun createPrimaryButton(label: String, intent: AiIntegrationsIntent): JButton = FilledActionButton(label).apply {
        addActionListener { intentListener(intent) }
    }

    private fun createSecondaryButton(label: String, icon: Icon, intent: AiIntegrationsIntent): JButton = OutlinedActionButton(label, icon).apply {
        addActionListener { intentListener(intent) }
    }

    private fun createCompactButton(label: String, intent: AiIntegrationsIntent): JButton = JButton(label).apply {
        margin = JBUI.insets(2, 8)
        addActionListener { intentListener(intent) }
    }

    private fun createLinkButton(label: String, intent: AiIntegrationsIntent): JButton = createLinkButton(label) {
        intentListener(intent)
    }

    private fun createLinkButton(label: String, action: () -> Unit): JButton = JButton(label).apply {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        margin = JBUI.emptyInsets()
        foreground = LINK_TEXT
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { action() }
    }

    override fun dispose() {
        disposed.set(true)
        intentListener = {}
    }

    internal data class RowAction(val label: String, val intent: AiIntegrationsIntent)

    companion object {
        internal const val WIDE_LAYOUT_THRESHOLD = 900
        private const val NARROW_CONTENT_WIDTH = 760
        private const val WIDE_CONTENT_WIDTH = 1160
        private const val REMOTE_MESSAGE = "Local AI integrations are unavailable in a remote IDE session."

        private val CARD_BACKGROUND = JBColor(Color(0xFFFFFF), Color(0x2B2D30))
        private val CARD_BORDER = JBColor(Color(0xDDE0E5), Color(0x45474D))
        private val ROW_BACKGROUND = JBColor(Color(0xF7F8FA), Color(0x323438))
        private val ROW_BORDER = JBColor(Color(0xE9EBEF), Color(0x3D3F44))
        private val SECONDARY_TEXT = JBColor(Color(0x5F6673), Color(0xA8ADBD))
        private val LINK_TEXT = JBColor(Color(0x0B6BCB), Color(0x6CAEFF))
    }

    private class ContentColumn : JPanel() {
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

    private class NaturalHeightPanel : JPanel() {
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}

private data class CardStatus(val text: String, val tone: StatusTone)

private enum class StatusTone {
    SUCCESS,
    WARNING,
    NEUTRAL
}

private class StatusPill(status: CardStatus) : JPanel() {
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
    private val radius: Int
) : JPanel() {
    init {
        isOpaque = false
    }

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

private class FilledActionButton(text: String) : JButton(text) {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isRolloverEnabled = true
        border = JBUI.Borders.empty(6, 13)
        margin = JBUI.emptyInsets()
        foreground = Color.WHITE
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2d = graphics.create() as Graphics2D
        graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2d.color = when {
            !isEnabled -> DISABLED_BACKGROUND
            model.isPressed -> PRESSED_BACKGROUND
            model.isRollover -> HOVER_BACKGROUND
            else -> BACKGROUND
        }
        graphics2d.fillRoundRect(0, 0, width, height, JBUI.scale(9), JBUI.scale(9))
        if (hasFocus()) {
            graphics2d.color = FOCUS_BORDER
            graphics2d.stroke = BasicStroke(JBUI.scale(2).toFloat())
            graphics2d.drawRoundRect(1, 1, width - 3, height - 3, JBUI.scale(9), JBUI.scale(9))
        }
        graphics2d.dispose()
        super.paintComponent(graphics)
    }

    companion object {
        private val BACKGROUND = JBColor(Color(0x0B6BCB), Color(0x3574C7))
        private val HOVER_BACKGROUND = JBColor(Color(0x095CAD), Color(0x417FD2))
        private val PRESSED_BACKGROUND = JBColor(Color(0x084D90), Color(0x2D65B2))
        private val DISABLED_BACKGROUND = JBColor(Color(0xA8B7C8), Color(0x505A6A))
        private val FOCUS_BORDER = JBColor(Color(0x8DC5FF), Color(0x9AC8FF))
    }
}

private class OutlinedActionButton(text: String, icon: Icon) : JButton(text, icon) {
    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        isRolloverEnabled = true
        border = JBUI.Borders.empty(5, 11)
        margin = JBUI.emptyInsets()
        iconTextGap = JBUI.scale(6)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2d = graphics.create() as Graphics2D
        graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (model.isPressed || model.isRollover) {
            graphics2d.color = if (model.isPressed) PRESSED_BACKGROUND else HOVER_BACKGROUND
            graphics2d.fillRoundRect(0, 0, width - 1, height - 1, JBUI.scale(9), JBUI.scale(9))
        }
        graphics2d.color = if (hasFocus()) FOCUS_BORDER else BORDER
        graphics2d.stroke = BasicStroke(JBUI.scale(if (hasFocus()) 2 else 1).toFloat())
        graphics2d.drawRoundRect(1, 1, width - 3, height - 3, JBUI.scale(9), JBUI.scale(9))
        graphics2d.dispose()
        super.paintComponent(graphics)
    }

    companion object {
        private val BORDER = JBColor(Color(0xB7BCC5), Color(0x5A5D63))
        private val FOCUS_BORDER = JBColor(Color(0x0B6BCB), Color(0x6CAEFF))
        private val HOVER_BACKGROUND = JBColor(Color(0xEDF5FD), Color(0x303B49))
        private val PRESSED_BACKGROUND = JBColor(Color(0xDDEEFF), Color(0x35465A))
    }
}

private fun AiIntegrationsPanel.cliStatus(state: AiIntegrationsPanelState): CardStatus = when (state) {
    AiIntegrationsPanelState.Loading -> CardStatus("Checking", StatusTone.NEUTRAL)
    AiIntegrationsPanelState.Remote -> CardStatus("Unavailable", StatusTone.NEUTRAL)
    is AiIntegrationsPanelState.Error -> CardStatus("Needs attention", StatusTone.WARNING)
    is AiIntegrationsPanelState.Empty -> state.snapshot.cli.toStatus()
    is AiIntegrationsPanelState.Ready -> state.snapshot.cli.toStatus()
}

private fun AiIntegrationsPanel.mcpStatus(state: AiIntegrationsPanelState): CardStatus = when (state) {
    AiIntegrationsPanelState.Loading -> CardStatus("Checking", StatusTone.NEUTRAL)
    AiIntegrationsPanelState.Remote -> CardStatus("Unavailable", StatusTone.NEUTRAL)
    is AiIntegrationsPanelState.Error -> CardStatus("Needs attention", StatusTone.WARNING)
    is AiIntegrationsPanelState.Empty -> CardStatus("No agents", StatusTone.NEUTRAL)
    is AiIntegrationsPanelState.Ready -> {
        val count = state.snapshot.agents.count { it.standaloneMcpSupported }
        if (count == 0) CardStatus("No agents", StatusTone.NEUTRAL) else CardStatus("$count available", StatusTone.SUCCESS)
    }
}

private fun CliState.toStatus(): CardStatus = when (installation) {
    CliInstallationState.NOT_INSTALLED -> CardStatus("Not installed", StatusTone.WARNING)
    CliInstallationState.UNUSABLE -> CardStatus("Needs attention", StatusTone.WARNING)
    CliInstallationState.INSTALLED -> when (authentication) {
        CliAuthenticationState.AUTHENTICATED -> CardStatus("Ready", StatusTone.SUCCESS)
        CliAuthenticationState.UNAUTHENTICATED,
        CliAuthenticationState.INVALID,
        CliAuthenticationState.UNVERIFIED -> CardStatus("Sign in required", StatusTone.WARNING)
        CliAuthenticationState.UNAVAILABLE,
        CliAuthenticationState.UNKNOWN -> CardStatus("Check status", StatusTone.NEUTRAL)
    }
}

private fun agentSummary(agentNames: List<String>, integrationName: String): String {
    val visibleAgents = agentNames.take(3).joinToString(", ")
    val remaining = agentNames.size - 3
    val suffix = if (remaining > 0) " + $remaining more" else ""
    return "$integrationName is available for $visibleAgents$suffix."
}

private fun bodyText(text: String, secondary: Boolean = false): JBTextArea = JBTextArea(text).apply {
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

private fun CliInstallationState.displayText() = when (this) {
    CliInstallationState.NOT_INSTALLED -> "Not installed"
    CliInstallationState.INSTALLED -> "Installed"
    CliInstallationState.UNUSABLE -> "Installed but unusable"
}

private fun CliAuthenticationState.displayText() = when (this) {
    CliAuthenticationState.AUTHENTICATED -> "Signed in"
    CliAuthenticationState.UNAUTHENTICATED -> "Not signed in"
    CliAuthenticationState.INVALID -> "Credentials invalid"
    CliAuthenticationState.UNVERIFIED -> "Connection not verified"
    CliAuthenticationState.UNAVAILABLE -> "Unavailable"
    CliAuthenticationState.UNKNOWN -> "Status unknown"
}
