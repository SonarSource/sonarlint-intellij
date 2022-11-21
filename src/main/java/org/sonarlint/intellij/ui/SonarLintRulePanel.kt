/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.SonarLintIcons
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.core.RuleDescription
import org.sonarlint.intellij.ui.ruledescription.RuleHtmlViewer
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.util.concurrent.TimeUnit


class SonarLintRulePanel(private val project: Project): JBPanel<SonarLintRulePanel>(BorderLayout()) {
    private var htmlViewer = RuleHtmlViewer(project)
    private var currentRuleKey: String? = null
    private var currentModule: Module? = null

    private val ruleNameLabel = JBLabel("")

    private val ruleTypeIcon = JBLabel()

    private val ruleTypeLabel = JBLabel()

    private val ruleSeverityIcon = JBLabel()

    private val ruleSeverityLabel = JBLabel("")

    private val ruleKeyLabel = JBLabel("")

    private val myLoadingDecorator: LoadingDecorator

    init {
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
                ruleNameLabel.font =
                    UIUtil.getLabelFont().deriveFont((UIUtil.getLabelFont().size2D + JBUIScale.scale(3))).deriveFont(Font.BOLD)
                add(ruleNameLabel, BorderLayout.NORTH)
                add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
                    add(ruleTypeIcon, HorizontalLayout.LEFT)
                    add(ruleTypeLabel.apply {
                        border = JBUI.Borders.emptyRight(10)
                    }, HorizontalLayout.LEFT)
                    add(ruleSeverityIcon, HorizontalLayout.LEFT)
                    add(ruleSeverityLabel, HorizontalLayout.LEFT)
                    add(ruleKeyLabel.apply {
                        border = JBUI.Borders.emptyLeft(10)
                    }, HorizontalLayout.CENTER)
                }, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(htmlViewer, BorderLayout.CENTER)
        }
        myLoadingDecorator = LoadingDecorator(panel, project, 0)
        myLoadingDecorator.loadingText = "Loading rule description..."
        add(myLoadingDecorator.component, BorderLayout.CENTER)
        setRuleKey(null, null)
    }

    fun setRuleKey(module: Module?, ruleKey: String?) {
        if (currentModule == module && currentRuleKey == ruleKey) {
            return
        }
        currentRuleKey = ruleKey
        currentModule = module
        if (module == null || ruleKey == null) {
            nothingToDisplay(false)
            return
        }
        myLoadingDecorator.startLoading(false)
        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "Loading rule description...", false) {
                override fun run(progressIndicator: ProgressIndicator) {
                    try {
                        SonarLintUtils.getService(project, ProjectBindingManager::class.java)
                            .getFacade(module)
                            .getActiveRuleDescription(ruleKey)
                            .thenAccept { ruleDescription: RuleDescription? ->
                                ApplicationManager.getApplication().invokeLater {
                                    if (ruleDescription == null) {
                                        nothingToDisplay(true)
                                    } else {
                                        updateRuleDescription(ruleDescription)
                                    }
                                    myLoadingDecorator.stopLoading()
                                }
                            }[30, TimeUnit.SECONDS]
                    } catch (e: Exception) {
                        SonarLintConsole.get(project).error("Cannot get rule description", e)
                        ApplicationManager.getApplication().invokeLater {
                            nothingToDisplay(true)
                        }
                    }
                }
            })
    }

    private fun nothingToDisplay(error: Boolean) {
        htmlViewer.clear()
        ruleNameLabel.text = if (error) "Couldn't find the rule description" else "Select an issue to display the rule description"
        ruleTypeIcon.icon = null
        ruleTypeLabel.text = ""
        ruleSeverityIcon.icon = null
        ruleSeverityLabel.text = ""
        ruleKeyLabel.text = ""
        revalidate()
    }

    private fun updateRuleDescription(ruleDescription: RuleDescription) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        updateHeader(ruleDescription)
        htmlViewer.updateHtml(ruleDescription)
        revalidate()
    }

    private fun updateHeader(ruleDescription: RuleDescription) {
        ruleNameLabel.text = ruleDescription.name
        ruleTypeIcon.icon = SonarLintIcons.type(ruleDescription.type)
        ruleTypeLabel.text = clean(ruleDescription.type)
        ruleSeverityIcon.icon = SonarLintIcons.severity(ruleDescription.severity)
        ruleSeverityLabel.text = clean(ruleDescription.severity)
        ruleKeyLabel.text = ruleDescription.key
    }

    private fun clean(txt: String): String {
        return StringUtil.capitalize(txt.lowercase().replace("_", " "))
    }

}