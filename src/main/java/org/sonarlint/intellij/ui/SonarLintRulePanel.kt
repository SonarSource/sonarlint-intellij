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
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.core.RuleDescription
import org.sonarlint.intellij.ui.ruledescription.RuleHeaderPanel
import org.sonarlint.intellij.ui.ruledescription.RuleHtmlViewer
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.concurrent.TimeUnit
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret


private const val RULE_CONFIG_LINK_PREFIX = "#rule:"

class SonarLintRulePanel(private val project: Project) : JBLoadingPanel(BorderLayout(), project) {

    private val htmlViewer = RuleHtmlViewer()
    private val ruleNameLabel = JBLabel()
    private val headerPanel = RuleHeaderPanel()
    private val paramsPanel = JBPanel<JBPanel<*>>(GridBagLayout())

    private var currentRuleKey: String? = null
    private var currentModule: Module? = null

    init {
        add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            ruleNameLabel.font =
                UIUtil.getLabelFont().deriveFont((UIUtil.getLabelFont().size2D + JBUIScale.scale(3))).deriveFont(Font.BOLD)
            add(ruleNameLabel, BorderLayout.NORTH)
            add(headerPanel, BorderLayout.CENTER)
        }, BorderLayout.NORTH)

        add(htmlViewer, BorderLayout.CENTER)

        paramsPanel.border = JBUI.Borders.emptyLeft(12)
        add(paramsPanel, BorderLayout.SOUTH)

        setLoadingText("Loading rule description...")
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
        startLoading()
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
                                    stopLoading()
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
        hideRuleParameters()
        ruleNameLabel.text = ""
        headerPanel.showMessage(if (error) "Couldn't find the rule description" else "Select an issue to display the rule description")
        revalidate()
    }

    private fun updateRuleDescription(ruleDescription: RuleDescription) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        updateHeader(ruleDescription)
        htmlViewer.updateHtml(ruleDescription.html)
        updateParams(ruleDescription)
        revalidate()
    }

    private fun updateHeader(ruleDescription: RuleDescription) {
        ruleNameLabel.text = ruleDescription.name
        headerPanel.update(ruleDescription.key, ruleDescription.type, ruleDescription.severity)
    }

    private fun updateParams(ruleDescription: RuleDescription) {
        hideRuleParameters()
        if (ruleDescription.params.isNotEmpty()) {
            populateParamPanel(ruleDescription)
        }
        paramsPanel.revalidate()
    }

    private fun hideRuleParameters() {
        paramsPanel.removeAll()
        paramsPanel.isVisible = false
    }

    private fun populateParamPanel(ruleDescription: RuleDescription) {
        paramsPanel.apply {
            isVisible = true
            border = IdeBorderFactory.createTitledBorder("Parameters")
            val constraints = GridBagConstraints()
            constraints.anchor = GridBagConstraints.BASELINE_LEADING
            constraints.gridy = 0
            for (param in ruleDescription.params) {
                val paramDefaultValue = param.defaultValue
                val defaultValue = paramDefaultValue ?: "(none)"
                val currentValue = Settings.getGlobalSettings().getRuleParamValue(ruleDescription.key, param.name).orElse(defaultValue)
                constraints.gridx = 0
                constraints.fill = GridBagConstraints.HORIZONTAL
                constraints.insets.right = UIUtil.DEFAULT_HGAP
                constraints.weightx = 0.0
                val paramName = JBLabel(param.name).apply {
                    toolTipText = param.description
                }
                add(paramName, constraints)
                constraints.gridx = 1
                constraints.weightx = 1.0
                constraints.insets.right = 0
                add(JBLabel(currentValue + (if (defaultValue != currentValue) " (default: $defaultValue)" else "")), constraints)
                constraints.gridy++
            }
            constraints.weightx = 0.0
            constraints.gridx = 0
            constraints.gridwidth = 2
            add(JEditorPane().apply {
                contentType = UIUtil.HTML_MIME
                (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
                editorKit = UIUtil.getHTMLEditorKit()
                addHyperlinkListener(RuleConfigHyperLinkListener(project))
                isEditable = false
                isOpaque = false
                isFocusable = false
                border = null
                SwingHelper.setHtml(
                    this,
                    """<small>Parameter values can be set in <a href="$RULE_CONFIG_LINK_PREFIX${ruleDescription.key}">Rule Settings</a>. 
            | In connected mode, server side configuration overrides local settings.</small>""".trimMargin(),
                    UIUtil.getLabelForeground()
                )
            }, constraints)
        }
    }

    private class RuleConfigHyperLinkListener(private val project: Project) : BrowserHyperlinkListener() {

        public override fun hyperlinkActivated(e: HyperlinkEvent) {
            if (e.description.startsWith(RULE_CONFIG_LINK_PREFIX)) {
                openRuleSettings(e.description.substringAfter(RULE_CONFIG_LINK_PREFIX))
                return
            }
            super.hyperlinkActivated(e)
        }

        private fun openRuleSettings(ruleKey: String?) {
            if (ruleKey != null) {
                val configurable = SonarLintGlobalConfigurable()
                ShowSettingsUtil.getInstance().editConfigurable(project, configurable) { configurable.selectRule(ruleKey) }
            }
        }
    }


}