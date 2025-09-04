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
package org.sonarlint.intellij.ui.traffic.light

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.event.AncestorEvent
import kotlin.math.max


class SonarLintDashboardPopup(private val editor: Editor) {

    private var onAncestorChangedListener = object : AncestorListenerAdapter() {
        override fun ancestorMoved(event: AncestorEvent) {
            hidePopup()
        }
    }
    private val popupAlarm: Alarm
    private var myPopup: JBPopup? = null
    private var insidePopup = false
    private val dashboard = SonarLintDashboardPanel(editor)

    init {
        dashboard.panel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(event: MouseEvent) {
                insidePopup = true
            }

            override fun mouseExited(event: MouseEvent) {
                val point = event.point
                if (point !in dashboard.panel.bounds || point.x == 0 || point.y == 0) {
                    insidePopup = false
                    if (canClose()) {
                        hidePopup()
                    }
                }
            }
        })

        val disposable = Disposer.newDisposable()
        EditorUtil.disposeWithEditor(editor, disposable)
        popupAlarm = Alarm(disposable)
    }

    fun scheduleShow(target: Component) {
        popupAlarm.cancelAllRequests()
        popupAlarm.addRequest({ showPopup(target) }, Registry.intValue("ide.tooltip.initialReshowDelay"))
    }

    private fun showPopup(target: Component) {
        hidePopup()
        val myContent = dashboard.panel

        val myPopupBuilder =
                JBPopupFactory.getInstance().createComponentPopupBuilder(myContent, null).setCancelOnClickOutside(true)

        val myPopupListener: JBPopupListener = object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                editor.component.removeAncestorListener(onAncestorChangedListener)
            }
        }

        myPopup = myPopupBuilder.createPopup()
        myPopup!!.addListener(myPopupListener)
        editor.component.addAncestorListener(onAncestorChangedListener)

        val size: Dimension = myContent.preferredSize
        size.width = max(size.width, JBUIScale.scale(296))
        val targetBottom = target.y + target.height
        val point = RelativePoint(editor.component, Point(editor.component.width - 10 - size.width, targetBottom + 5))
        myPopup!!.size = size

        myPopup!!.show(point)
    }

    fun scheduleHide() {
        popupAlarm.cancelAllRequests()
        popupAlarm.addRequest({
            if (canClose()) {
                hidePopup()
            }
        }, Registry.intValue("ide.tooltip.initialDelay.highlighter"))
    }

    private fun canClose(): Boolean {
        return !insidePopup
    }

    fun hidePopup() {
        if (myPopup != null && !myPopup!!.isDisposed) {
            myPopup!!.cancel()
        }
        myPopup = null
    }

    fun refresh(model: SonarLintDashboardModel) {
        dashboard.refresh(model)
    }

}
