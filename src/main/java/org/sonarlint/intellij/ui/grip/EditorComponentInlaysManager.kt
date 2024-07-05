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
import javax.swing.ScrollPaneConstants
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get

// Inspired from https://github.com/cursive-ide/component-inlay-example and com.intellij.util.ui.codereview.diff.EditorComponentInlaysManager
class EditorComponentInlaysManager(val editor: EditorImpl) : Disposable {

    private val managedInlays = mutableMapOf<ComponentWrapper, Disposable>()
    private val editorWidthWatcher = EditorTextWidthWatcher()

    init {
        editor.scrollPane.viewport.addComponentListener(editorWidthWatcher)
        Disposer.register(this) {
            editor.scrollPane.viewport.removeComponentListener(editorWidthWatcher)
        }

        EditorUtil.disposeWithEditor(editor, this)
    }

    @RequiresEdt
    fun insertBefore(lineIndex: Int, component: InlayQuickFixPanel): Disposable? {
        return try {
            if (Disposer.isDisposed(this)) return null

            val wrappedComponent = ComponentWrapper(component)
            val offset = editor.document.getLineEndOffset(lineIndex)

            EditorEmbeddedComponentManager.getInstance()
                .addComponent(
                    editor, wrappedComponent,
                    EditorEmbeddedComponentManager.Properties(
                        EditorEmbeddedComponentManager.ResizePolicy.none(),
                        null,
                        true,
                        true,
                        0,
                        offset
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
            get(project).simpleNotification(
                null,
                "Fix became invalid. Please refresh the AI suggestions.",
                NotificationType.WARNING
            )
            null
        }
    }

    private inner class ComponentWrapper(val component: InlayQuickFixPanel) : JBScrollPane(component) {
        init {
            isOpaque = false
            viewport.isOpaque = false

            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()

            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.preferredSize = Dimension(0, 0)
            setViewportView(component)

            component.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) =
                    dispatchEvent(ComponentEvent(component, ComponentEvent.COMPONENT_RESIZED))
            })
        }

        override fun getPreferredSize(): Dimension {
            return Dimension(min(component.preferredSize.width, editorWidthWatcher.editorTextWidth), component.preferredSize.height)
        }
    }

    override fun dispose() {
        managedInlays.values.forEach(Disposer::dispose)
    }

    private inner class EditorTextWidthWatcher : ComponentAdapter() {

        var editorTextWidth: Int = 0

        private val maximumEditorTextWidth: Int
        private val verticalScrollbarFlipped: Boolean

        init {
            val metrics = editor.getFontMetrics(Font.PLAIN)
            val spaceWidth = FontLayoutService.getInstance().charWidth2D(metrics, ' '.code)
            // -4 to create some space
            maximumEditorTextWidth = ceil(spaceWidth * (editor.settings.getRightMargin(editor.project)) - 4).toInt()

            val scrollbarFlip = editor.scrollPane.getClientProperty(JBScrollPane.Flip::class.java)
            verticalScrollbarFlipped =
                scrollbarFlip == JBScrollPane.Flip.HORIZONTAL || scrollbarFlip == JBScrollPane.Flip.BOTH
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
            return min(max(visibleEditorTextWidth, 0), maximumEditorTextWidth)
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
        private val INLAYS_KEY: Key<EditorComponentInlaysManager> = Key.create("EditorComponentInlaysManager")

        fun from(editor: Editor): EditorComponentInlaysManager {
            return synchronized(editor) {
                var manager = editor.getUserData(INLAYS_KEY)
                if (manager != null && Disposer.isDisposed(manager)) {
                    manager = null
                }
                if (manager == null) {
                    val newManager = EditorComponentInlaysManager(editor as EditorImpl)
                    editor.putUserData(INLAYS_KEY, newManager)
                    newManager
                } else manager
            }
        }
    }
}
