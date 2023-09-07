package org.sonarlint.intellij.ui.traffic.light

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.PopupState
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
    private val myPopupState = PopupState.forPopup()
    private val popupAlarm = Alarm()
    private var myPopup: JBPopup? = null
    private var insidePopup = false
    private val dashboard = SonarLintDashboard(editor)

    init {
        dashboard.panel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(event: MouseEvent) {
                insidePopup = true
            }

            override fun mouseExited(event: MouseEvent) {
                val point = event.point
                if (!dashboard.panel.bounds.contains(point) || point.x == 0 || point.y == 0) {
                    insidePopup = false
                    if (canClose()) {
                        hidePopup()
                    }
                }
            }
        })
    }

    fun scheduleShow(target: Component) {
        popupAlarm.cancelAllRequests()
        popupAlarm.addRequest({ showPopup(target) }, Registry.intValue("ide.tooltip.initialReshowDelay"))
    }

    private fun showPopup(target: Component) {
        hidePopup()
        if (myPopupState.isRecentlyHidden) return  // do not show new popup
        updateContentPanel()
        val myContent = dashboard.panel
        val myPopupBuilder =
            JBPopupFactory.getInstance().createComponentPopupBuilder(myContent, null).setCancelOnClickOutside(true)
//                .setCancelCallback { analyzerStatus.controller.canClosePopup() }
        val myPopupListener: JBPopupListener = object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
//                analyzerStatus.controller.onClosePopup()
                editor.component.removeAncestorListener(onAncestorChangedListener)
            }
        }
        myPopup = myPopupBuilder.createPopup()
        myPopup!!.addListener(myPopupListener)
        myPopupState.prepareToShow(myPopup!!)
        editor.component.addAncestorListener(onAncestorChangedListener)
        val size: Dimension = myContent.preferredSize
        size.width = max(size.width, JBUIScale.scale(296))
        val targetBottom = target.y + target.height
        val point = RelativePoint(editor.component, Point(editor.component.width - 10 - size.width, targetBottom + 5))
        myPopup!!.size = size
        myPopup!!.show(point)
    }

    private fun updateContentPanel() {
        dashboard.refresh()
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
        return !insidePopup/* && levelLinks.stream().allMatch(Predicate<DropDownLink<*>> { l: DropDownLink<*> -> l.popupState.isHidden })*/
    }

    fun hidePopup() {
        if (myPopup != null && !myPopup!!.isDisposed()) {
            myPopup!!.cancel()
        }
        myPopup = null
    }
}
