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
package org.sonarlint.intellij.ui.page.settings

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.Component
import java.awt.Window
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JEditorPane
import javax.swing.JRadioButton
import javax.swing.text.JTextComponent
import org.awaitility.Awaitility
import org.sonarlint.intellij.fixtures.findComponents
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fixtures.walkComponents

/** Outermost tab strip (walk order is top-down from the settings root). */
internal fun Component.findTabbedPane(): JBTabbedPane =
    walkComponents().filterIsInstance<JBTabbedPane>().first()

internal fun JBTabbedPane.selectTab(title: String) {
    val index = (0 until tabCount).first { getTitleAt(it) == title }
    selectedIndex = index
}

internal fun Component.selectedTabContent(): Component =
    findTabbedPane().selectedComponent ?: error("No tab selected")

/** First toolbar **Add** button ([ToolbarDecorator] order: add, remove, edit). */
internal fun Component.findToolbarAddButton(): Component {
    exclusionsToolbarHost()?.let { host ->
        host.walkComponents()
            .firstOrNull { it.javaClass.simpleName.endsWith("ActionButton") }
            ?.let { return it }
        host.walkComponents()
            .filterIsInstance<ActionToolbarImpl>()
            .firstOrNull()
            ?.let { toolbar ->
                toolbar.updateActionsImmediately()
                toolbar.walkComponents()
                    .firstOrNull { it.javaClass.simpleName.endsWith("ActionButton") }
                    ?.let { return it }
            }
    }
    walkComponents()
        .filterIsInstance<CommonActionsPanel>()
        .firstOrNull()
        ?.let { return it.resolveAddButton() }
    val sampleTypes = walkComponents().map { it.javaClass.simpleName }.distinct().take(40).toList()
    error("No toolbar add button in settings UI (sample component types: $sampleTypes)")
}

private fun Component.exclusionsToolbarHost(): Component? {
    walkComponents()
        .filterIsInstance<JBTable>()
        .firstOrNull { it.columnCount == 2 && it.getColumnName(0) == "Type" }
        ?.parent
        ?.let { return it }
    walkComponents()
        .filterIsInstance<JBList<*>>()
        .firstOrNull { list ->
            list.parent?.walkComponents()?.any { it is ActionToolbarImpl } == true
        }
        ?.parent
        ?.let { return it }
    return null
}

internal fun Component.triggerExclusionsAddAction(project: Project) {
    val selectedTab = findTabbedPane().selectedComponent ?: error("No selected settings tab")
    val toolbar = selectedTab.walkComponents()
        .filterIsInstance<ActionToolbarImpl>()
        .firstOrNull()
        ?: error("Exclusions toolbar not found on selected tab")
    toolbar.updateActionsImmediately()
    val actionsMethod = toolbar.javaClass.methods.first { it.name == "getActions" && it.parameterCount == 0 }
    @Suppress("UNCHECKED_CAST")
    val actions = actionsMethod.invoke(toolbar) as List<AnAction>
    val event = AnActionEvent.createFromDataContext(
        ActionPlaces.UNKNOWN,
        null,
        DataContext { dataId ->
            if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
        },
    )
    actions.first().actionPerformed(event)
}

private fun CommonActionsPanel.resolveAddButton(): Component {
    javaClass.methods
        .asSequence()
        .filter { it.parameterCount == 0 && it.name.contains("Add", ignoreCase = true) }
        .mapNotNull { method -> method.invoke(this) as? Component }
        .firstOrNull()
        ?.let { return it }
    javaClass.declaredFields
        .asSequence()
        .filter { it.name.contains("Add", ignoreCase = true) }
        .mapNotNull { field ->
            field.isAccessible = true
            field.get(this) as? Component
        }
        .firstOrNull()
        ?.let { return it }
    error("Could not resolve add button on CommonActionsPanel")
}

internal fun Component.triggerToolbarButtonClick() {
    when (this) {
        is JButton -> doClick()
        else -> {
            val click = javaClass.methods.firstOrNull { it.name == "click" && it.parameterCount == 0 }
            if (click != null) {
                click.invoke(this)
            } else {
                error("Could not trigger ${javaClass.name}")
            }
        }
    }
}

internal fun findShowingJDialog(titleContains: String): JDialog? =
    Window.getWindows()
        .filterIsInstance<JDialog>()
        .firstOrNull { it.isShowing && it.title.contains(titleContains, ignoreCase = true) }
        ?: Window.getWindows()
            .filterIsInstance<JDialog>()
            .firstOrNull { it.isShowing && titleContains.contains("Exclusion", ignoreCase = true) && it.title.contains("Exclusion", ignoreCase = true) }

internal fun JDialog.findOkButton(): JButton =
    rootPane.findComponents<JButton>().first { it.text == "OK" && it.isEnabled }

internal fun JDialog.findTextField(): JBTextField =
    rootPane.findComponents<JBTextField>().first()

internal fun JDialog.findRadioButton(labelContains: String): JRadioButton =
    rootPane.findComponents<JRadioButton>().first { it.text.contains(labelContains, ignoreCase = true) }

internal fun JDialog.findTextInput(): JTextComponent =
    rootPane.findComponents<JTextComponent>().first { it.isEnabled && it.isShowing }

internal data class ModalDialogSession(
    val dialog: JDialog,
    private val openerThread: Thread,
    private val openerError: AtomicReference<Throwable?>,
) {
    fun awaitClosed(timeoutMillis: Long = 120_000) {
        openerThread.join(timeoutMillis)
        openerError.get()?.let { throw it }
        check(!openerThread.isAlive) {
            "Modal dialog did not close within ${timeoutMillis}ms"
        }
    }
}

/** Opens a modal dialog on a worker EDT task and returns while the dialog is still visible. */
internal fun openModalDialog(
    openModalAction: () -> Unit,
    dialogTitleContains: String,
    timeoutSeconds: Long = 30,
): ModalDialogSession {
    val openerError = AtomicReference<Throwable?>(null)
    val opener = Thread {
        try {
            readOnEdt {
                openModalAction()
            }
        } catch (t: Throwable) {
            openerError.set(t)
        }
    }
    opener.isDaemon = true
    opener.start()
    Awaitility.await().atMost(timeoutSeconds, TimeUnit.SECONDS).until {
        readOnEdt {
            repeat(10) { pumpEdt() }
            findShowingJDialog(dialogTitleContains) != null
        }
    }
    val dialog = readOnEdt {
        findShowingJDialog(dialogTitleContains) ?: error("Dialog '$dialogTitleContains' not found")
    }
    openerError.get()?.let { throw it }
    return ModalDialogSession(dialog, opener, openerError)
}

/**
 * Clicks [openModalAction] on a worker thread (it usually blocks on a modal dialog), then runs [interactWithModal]
 * from the test thread while the dialog is open, and waits until the modal closes.
 */
internal fun <T> interactWithModalDialog(
    openModalAction: () -> Unit,
    dialogTitleContains: String,
    timeoutSeconds: Long = 30,
    interactWithModal: (JDialog) -> T,
): T {
    val session = openModalDialog(openModalAction, dialogTitleContains, timeoutSeconds)
    val result = readOnEdt { interactWithModal(session.dialog) }
    session.awaitClosed(timeoutSeconds * 1000)
    return result
}
