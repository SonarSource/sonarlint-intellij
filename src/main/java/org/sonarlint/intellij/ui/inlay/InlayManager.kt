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
package org.sonarlint.intellij.ui.inlay

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get

// Inspired from https://github.com/cursive-ide/component-inlay-example and com.intellij.util.ui.codereview.diff.EditorComponentInlaysManager
class InlayManager(val editor: EditorImpl) : Disposable {

    var disposed = false
    private val managedInlays = ConcurrentHashMap<ComponentWrapper, Disposable>()
    private val editorWidthWatcher = EditorTextWidthWatcher()

    init {
        editor.scrollPane.viewport.addComponentListener(editorWidthWatcher)
        Disposer.register(this) {
            editor.scrollPane.viewport.removeComponentListener(editorWidthWatcher)
        }

        EditorUtil.disposeWithEditor(editor, this)
    }

    @RequiresEdt
    fun insertBefore(lineStartOffset: Int, component: FixSuggestionInlayPanel): Disposable? {
        return try {
            if (disposed) return null

            val wrappedComponent = ComponentWrapper(component)

            EditorEmbeddedComponentManager.getInstance()
                .addComponent(
                    editor, wrappedComponent,
                    EditorEmbeddedComponentManager.Properties(
                        EditorEmbeddedComponentManager.ResizePolicy.none(),
                        null,
                        true,
                        true,
                        0,
                        lineStartOffset
                    )
                )?.also {
                    managedInlays[wrappedComponent] = it
                    Disposer.register(it) {
                        wrappedComponent.component.dispose()
                        managedInlays.remove(wrappedComponent)
                    }
                }
        } catch (e: IndexOutOfBoundsException) {
            val project = editor.project ?: return null
            SonarLintConsole.get(project).error("Fix is invalid", e)
            get(project).simpleNotification(
                null,
                "Fix became invalid. Please refresh the AI suggestions.",
                NotificationType.WARNING
            )
            null
        }
    }

    private inner class ComponentWrapper(val component: FixSuggestionInlayPanel) : JBScrollPane(component) {
        init {
            isOpaque = false
            viewport.isOpaque = false

            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()

            horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.preferredSize = Dimension(0, 0)
            setViewportView(component)

            component.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) =
                    dispatchEvent(ComponentEvent(component, ComponentEvent.COMPONENT_RESIZED))
            })
        }

        override fun getPreferredSize(): Dimension {
            return Dimension(editorWidthWatcher.editorTextWidth, component.preferredSize.height)
        }
    }

    override fun dispose() {
        managedInlays.values.forEach(Disposer::dispose)
        disposed = true
    }

    private inner class EditorTextWidthWatcher : ComponentAdapter() {
        var editorTextWidth: Int = 0

        private val maximumEditorTextWidth: Int
        private val verticalScrollbarFlipped: Boolean

        init {
            val metrics = editor.getFontMetrics(Font.PLAIN)
            val spaceWidth = FontLayoutService.getInstance().charWidth2D(metrics, ' '.code)
            maximumEditorTextWidth = ceil(spaceWidth * (editor.settings.getRightMargin(editor.project)) - 4).toInt()

            val scrollbarFlip = editor.scrollPane.getClientProperty(JBScrollPane.Flip::class.java)
            verticalScrollbarFlipped = scrollbarFlip == JBScrollPane.Flip.HORIZONTAL || scrollbarFlip == JBScrollPane.Flip.BOTH
        }

        override fun componentResized(e: ComponentEvent) = updateWidthForAllInlays()
        override fun componentHidden(e: ComponentEvent) = updateWidthForAllInlays()
        override fun componentShown(e: ComponentEvent) = updateWidthForAllInlays()

        private fun updateWidthForAllInlays() {
            val newWidth = calcWidth()
            if (editorTextWidth == newWidth) return
            editorTextWidth = newWidth

            managedInlays.keys.forEach {
                it.dispatchEvent(ComponentEvent(it, ComponentEvent.COMPONENT_RESIZED))
                it.invalidate()
            }
        }

        private fun calcWidth(): Int {
            val visibleEditorTextWidth = editor.scrollPane.viewport.width - getVerticalScrollbarWidth() - getGutterTextGap()
            return min(visibleEditorTextWidth, maximumEditorTextWidth)
        }

        private fun getVerticalScrollbarWidth(): Int {
            val width = editor.scrollPane.verticalScrollBar.width
            return if (!verticalScrollbarFlipped) width * 2 else width
        }

        private fun getGutterTextGap(): Int {
            return if (verticalScrollbarFlipped) {
                val gutter = (editor as EditorEx).gutterComponentEx
                gutter.width - gutter.whitespaceSeparatorOffset
            } else 0
        }
    }

    companion object {
        private val INLAYS_KEY: Key<InlayManager> = Key.create("EditorComponentInlaysManager")

        fun from(editor: Editor): InlayManager {
            return synchronized(editor) {
                var manager = editor.getUserData(INLAYS_KEY)
                if (manager != null && manager.disposed) {
                    manager = null
                }
                if (manager == null) {
                    val newManager = InlayManager(editor as EditorImpl)
                    editor.putUserData(INLAYS_KEY, newManager)
                    newManager
                } else manager
            }
        }
    }

}
