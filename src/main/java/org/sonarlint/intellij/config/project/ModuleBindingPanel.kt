/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.config.project

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.ui.AnActionButton
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.core.SonarLintEngineManager
import org.sonarlint.intellij.tasks.ServerDownloadProjectTask
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.max
import kotlin.math.min

class ModuleBindingPanel(private val project: Project, currentConnectionSupplier: () -> ServerConnection?) {
    private var projectKeyTextField = JBTextField()
    val rootPanel: JPanel = JPanel(BorderLayout())
    private val modulesList: JBList<ModuleBinding>
    private val modulesListModel = CollectionListModel<ModuleBinding>()
    private val moduleBindingDetailsPanel = JPanel(GridBagLayout())
    private val detailsContainer = JBPanelWithEmptyText(BorderLayout())

    init {
        rootPanel.isVisible = SonarLintUtils.isModuleLevelBindingEnabled() && ModuleManager.getInstance(project).modules.size > 1
        modulesList = JBList()
        if (ProjectAttachProcessor.canAttachToProject()) {
            rootPanel.border = IdeBorderFactory.createTitledBorder("Override binding of attached project(s)")
            modulesList.emptyText.text = "No overridden attached project(s) binding"
            detailsContainer.withEmptyText("Select an attached project in the list")
        } else {
            rootPanel.border = IdeBorderFactory.createTitledBorder("Override binding per-module")
            modulesList.emptyText.text = "No overridden module binding"
            detailsContainer.withEmptyText("Select a module in the list")
        }
        modulesList.model = modulesListModel
        modulesList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                onModuleBindingSelectionChanged()
            }
        }
        modulesList.cellRenderer = object : ColoredListCellRenderer<ModuleBinding>() {
            override fun customizeCellRenderer(
                list: JList<out ModuleBinding>,
                module: ModuleBinding,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                icon = AllIcons.Nodes.Module
                append(module.module.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }

        val toolbarDecorator: ToolbarDecorator = ToolbarDecorator.createDecorator(modulesList)
            .setAddAction(AddModuleAction(project, modulesList))
            .setRemoveAction(RemoveModuleAction(modulesList))
            .disableUpDownActions()

        projectKeyTextField = JBTextField()
        projectKeyTextField.emptyText.text = "Input project key or search one"
        val projectKeyLabel = JLabel("Project key:")
        projectKeyLabel.labelFor = projectKeyTextField
        val insets = JBUI.insets(2, 0, 0, 0)
        moduleBindingDetailsPanel.add(
            projectKeyLabel, GridBagConstraints(
            0, 0, 1, 1, 0.0, 0.0,
            GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0
        )
        )
        moduleBindingDetailsPanel.add(
            projectKeyTextField, GridBagConstraints(
            1, 0, 1, 1, 1.0, 0.0,
            GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0
        )
        )
        val searchProjectKeyButton = JButton()
        moduleBindingDetailsPanel.add(
            searchProjectKeyButton, GridBagConstraints(
            2, 0, 1, 1, 0.0, 0.0,
            GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0
        )
        )
        // fill remaining space
        moduleBindingDetailsPanel.add(
            JPanel(), GridBagConstraints(
            0, 1, 3, 1, 0.0, 1.0,
            GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0
        )
        )
        detailsContainer.add(moduleBindingDetailsPanel)
        moduleBindingDetailsPanel.isVisible = false

        searchProjectKeyButton.action = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val selectedConnection: ServerConnection = currentConnectionSupplier() ?: return
                val map: Map<String, ServerProject> = downloadProjectList(project, selectedConnection) ?: return
                val dialog = SearchProjectKeyDialog(
                    rootPanel,
                    projectKeyTextField.text,
                    map,
                    selectedConnection.isSonarCloud
                )
                if (dialog.showAndGet() && dialog.selectedProjectKey != null) {
                    projectKeyTextField.text = dialog.selectedProjectKey
                }
            }
        }
        searchProjectKeyButton.text = "Search in list..."

        projectKeyTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                updateSelectedModuleBinding()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                updateSelectedModuleBinding()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                updateSelectedModuleBinding()
            }

            private fun updateSelectedModuleBinding() {
                getSelectedModuleBinding()?.let { it.sonarProjectKey = projectKeyTextField.text }
            }
        })

        val splitter = Splitter(false)
        splitter.firstComponent = toolbarDecorator.createPanel()
        splitter.secondComponent = detailsContainer
        splitter.proportion = 0.4f

        rootPanel.add(splitter, BorderLayout.CENTER)
    }

    private fun downloadProjectList(
        project: Project,
        selectedConnection: ServerConnection,
    ): Map<String, ServerProject>? {
        val engine = getService(SonarLintEngineManager::class.java).getConnectedEngine(selectedConnection.name)
        val downloadTask = ServerDownloadProjectTask(project, engine, selectedConnection)
        return try {
            ProgressManager.getInstance().run(downloadTask)
            downloadTask.result
        } catch (e: Exception) {
            val msg = if (e.message != null) e.message else "Failed to download list of projects"
            Messages.showErrorDialog(rootPanel, msg, "Error Downloading Project List")
            null
        }
    }

    private fun getSelectedModuleBinding(): ModuleBinding? = modulesList.selectedValue

    private fun onModuleBindingSelectionChanged() {
        val selectedModuleBinding = getSelectedModuleBinding()
        if (selectedModuleBinding == null) {
            moduleBindingDetailsPanel.isVisible = false
        } else {
            moduleBindingDetailsPanel.isVisible = true
            projectKeyTextField.text = selectedModuleBinding.sonarProjectKey
        }
        detailsContainer.repaint()
    }

    fun load(moduleOverrides: Map<Module, String>) {
        val bindings = moduleOverrides.map { ModuleBinding(it.key, it.value) }
        modulesListModel.add(bindings)
        moduleBindingDetailsPanel.isEnabled = bindings.isNotEmpty()
    }

    fun setEnabled(enabled: Boolean) {
        rootPanel.isEnabled = enabled
        modulesList.isEnabled = enabled
        if (!enabled) {
            modulesList.clearSelection()
        }
    }

    fun getModuleBindings(): List<ModuleBinding> = modulesListModel.items

    fun isModified(): Boolean {
        val moduleBindingsFromSettings = getService(project, ProjectBindingManager::class.java)
            .moduleOverrides
            .map { ModuleBinding(it.key, it.value) }.toSet()
        val moduleBindingsFromPanel = getModuleBindings().toSet()
        return moduleBindingsFromSettings != moduleBindingsFromPanel
    }

    private class AddModuleAction(private val project: Project, private val modulesList: JBList<ModuleBinding>) :
        AnActionButtonRunnable {
        override fun run(t: AnActionButton) {
            val collectionListModel = modulesList.model as CollectionListModel<ModuleBinding>
            val existingModuleNames = collectionListModel.items.map { it.module.name }
            val modulesToPickFrom =
                ModuleManager.getInstance(project).modules.filter {
                    !existingModuleNames.contains(it.name) &&
                        getService(it, ModuleBindingManager::class.java).isBindingOverrideAllowed()
                }
            val dialog = if (ProjectAttachProcessor.canAttachToProject())
                ChooseModulesDialog(modulesList, modulesToPickFrom.toMutableList(), "Select attached project", "Select the attached project for which you want to override the binding")
            else
                ChooseModulesDialog(modulesList, modulesToPickFrom.toMutableList(), "Select module", "Select the module for which you want to override the binding")
            dialog.setSingleSelectionMode()
            dialog.show()
            dialog.chosenElements.firstOrNull()?.let {
                collectionListModel.add(ModuleBinding(it))
                modulesList.setSelectedIndex(modulesList.model.size - 1)
            }
        }
    }

    private class RemoveModuleAction(private val modulesList: JBList<ModuleBinding>) : AnActionButtonRunnable {
        override fun run(t: AnActionButton) {
            val selectedIndex = modulesList.selectedIndex
            val model = modulesList.model as CollectionListModel<ModuleBinding>
            model.remove(modulesList.selectedValue)

            if (model.size > 0) {
                val newIndex = min(model.size - 1, max(selectedIndex - 1, 0))
                modulesList.setSelectedValue(model.getElementAt(newIndex), true)
            }
        }
    }

    data class ModuleBinding(
        val module: Module,
        var sonarProjectKey: String? = null,
    )
}
