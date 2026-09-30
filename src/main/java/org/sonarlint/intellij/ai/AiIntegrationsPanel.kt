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
import java.awt.BasicStroke
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
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus

class AiIntegrationsPanel : JBPanel<AiIntegrationsPanel>(BorderLayout()), Disposable {
    private val cards = ContentColumn()
    private val disposed = AtomicBoolean()
    private var intentListener: (AiIntegrationsIntent) -> Unit = {}
    private var currentState: AiIntegrationsPanelState = AiIntegrationsPanelState.Loading
    private var wide = false
    private var integrationCards: JPanel? = null

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
        add(bodyText("Bring SonarQube to your AI agents").apply {
            font = JBFont.label().asBold().biggerOn(7.0f)
        })
        add(verticalSpace(7))
        add(bodyText("Find and fix issues as your agent writes code.", secondary = true))
    }
    private fun createCliCard(state: AiIntegrationsPanelState): JPanel = createCard(
        "SonarQube CLI",
        "Catch issues as AI writes code",
        bodyText("Give your agents local code analysis to find bugs and vulnerabilities before you commit.", secondary = true),
        cliStatus(state)
    )

    private fun createMcpCard(state: AiIntegrationsPanelState): JPanel = createCard(
        "SonarQube MCP Server",
        "Bring project context to your agent",
        bodyText("Ask your agent about issues, quality gates, and coverage in your SonarQube projects.", secondary = true),
        mcpStatus(state)
    )

    private fun createCard(title: String, headline: String, description: JComponent, status: CardStatus): JPanel {
        val body = RoundedSurfacePanel(CARD_BACKGROUND, CARD_BORDER, 16).apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(18, 20, 12, 20)
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        body.add(createCardHeader(title, headline, description, status), BorderLayout.NORTH)
        return body
    }

    private fun createCardHeader(title: String, headline: String, description: JComponent, status: CardStatus): JPanel = JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(JPanel().apply {
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
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(StatusPill(status), BorderLayout.NORTH)
        }, BorderLayout.EAST)
    }

    private fun createPageFooter(): JPanel = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(36))
        add(createSecondaryButton("Refresh", AllIcons.Actions.Refresh, AiIntegrationsIntent.Refresh))
        add(Box.createHorizontalGlue())
    }
    private fun createSecondaryButton(label: String, icon: Icon, intent: AiIntegrationsIntent): JButton = OutlinedActionButton(label, icon).apply {
        addActionListener { intentListener(intent) }
    }

    override fun dispose() {
        disposed.set(true)
        intentListener = {}
    }

    companion object {
        internal const val WIDE_LAYOUT_THRESHOLD = 900
        private const val NARROW_CONTENT_WIDTH = 760
        private const val WIDE_CONTENT_WIDTH = 1160

        private val CARD_BACKGROUND = JBColor(Color(0xFFFFFF), Color(0x2B2D30))
        private val CARD_BORDER = JBColor(Color(0xDDE0E5), Color(0x45474D))
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

    private class WidthTrackingScrollContent : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)

        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
            (visibleRect.height - JBUI.scale(16)).coerceAtLeast(JBUI.scale(16))

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false
    }

    private class NaturalHeightPanel : JPanel() {
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}

private data class CardStatus(val text: String, val tone: StatusTone)

private const val NEEDS_ATTENTION = "Needs attention"

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
    is AiIntegrationsPanelState.Error -> CardStatus(NEEDS_ATTENTION, StatusTone.WARNING)
    is AiIntegrationsPanelState.Empty -> state.snapshot.cli.toStatus()
    is AiIntegrationsPanelState.Ready -> state.snapshot.cli.toStatus()
}

private fun AiIntegrationsPanel.mcpStatus(state: AiIntegrationsPanelState): CardStatus = when (state) {
    AiIntegrationsPanelState.Loading -> CardStatus("Checking", StatusTone.NEUTRAL)
    is AiIntegrationsPanelState.Error -> CardStatus(NEEDS_ATTENTION, StatusTone.WARNING)
    is AiIntegrationsPanelState.Empty -> CardStatus("0 supported", StatusTone.NEUTRAL)
    is AiIntegrationsPanelState.Ready -> {
        val count = state.snapshot.agents.count { it.standaloneMcpSupported }
        CardStatus("$count supported", StatusTone.NEUTRAL)
    }
}

private fun CliState.toStatus(): CardStatus = when (installation) {
    CliInstallationStatus.NOT_INSTALLED -> CardStatus("Not detected", StatusTone.WARNING)
    CliInstallationStatus.UNUSABLE -> CardStatus("Unavailable", StatusTone.WARNING)
    CliInstallationStatus.INSTALLED -> CardStatus("Installed", StatusTone.SUCCESS)
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
