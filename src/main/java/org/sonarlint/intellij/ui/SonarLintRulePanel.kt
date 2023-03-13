/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.ruledescription.RuleHeaderPanel
import org.sonarlint.intellij.ui.ruledescription.RuleHtmlViewer
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleContextualSectionDto
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDescriptionTabDto
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDetailsDto
import org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.concurrent.TimeUnit
import javax.swing.DefaultComboBoxModel
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret


private const val RULE_CONFIG_LINK_PREFIX = "#rule:"

class SonarLintRulePanel(private val project: Project) : JBLoadingPanel(BorderLayout(), project) {

    private val descriptionPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val ruleNameLabel = JBLabel()
    private val headerPanel = RuleHeaderPanel()
    private val paramsPanel = JBPanel<JBPanel<*>>(GridBagLayout())
    private val securityHotspotHeaderMessage = JEditorPane()
    private val ruleDetailsLoader = RuleDetailsLoader()
    private var finding: Finding? = null
    private var ruleDetails: ActiveRuleDetailsDto? = null

    init {
        add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(ruleNameLabel.apply {
                font = UIUtil.getLabelFont().deriveFont((UIUtil.getLabelFont().size2D + JBUIScale.scale(3))).deriveFont(Font.BOLD)
            }, BorderLayout.NORTH)
            add(headerPanel, BorderLayout.CENTER)
            add(securityHotspotHeaderMessage.apply {
                contentType = UIUtil.HTML_MIME
                (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
                editorKit = UIUtil.getHTMLEditorKit()
                border = JBUI.Borders.empty()
                isEditable = false
                isOpaque = false
                isFocusable = false

                addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
            }, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)

        add(descriptionPanel, BorderLayout.CENTER)

        add(paramsPanel.apply {
            border = IdeBorderFactory.createTitledBorder("Parameters")
        }, BorderLayout.SOUTH)

        setLoadingText("Loading rule description...")
        clear()
    }

    private data class RuleDetailsLoaderState(val lastModule: Module?, val lastFindingRuleKey: String?, val lastContextKey: String?)

    /**
     * To avoid useless calls to the backend/server, we cache parameters that may produce different rule details.
     * If the state has not changed, we don't reload rule details.
     */
    inner class RuleDetailsLoader {

        private var state = RuleDetailsLoaderState(null, null, null)

        fun clearState() {
            state = RuleDetailsLoaderState(null, null, null)
        }

        fun updateActiveRuleDetailsIfNeeded(module: Module, finding: Finding) {
            val newState = RuleDetailsLoaderState(module, finding.getRuleKey(), finding.getRuleDescriptionContextKey())
            if (state == newState) {
                // Still force a refresh of the UI, as some other fields of the finding may be differents
                runOnUiThread(project) {
                    updateUiComponents()
                }
                return
            }
            state = newState
            startLoading()
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading rule description...", false) {
                override fun run(progressIndicator: ProgressIndicator) {
                    SonarLintUtils.getService(BackendService::class.java).getActiveRuleDetails(module, finding.getRuleKey(), finding.getRuleDescriptionContextKey())
                        .orTimeout(30, TimeUnit.SECONDS)
                        .handle { response, error ->
                            stopLoading()
                            if (error != null) {
                                SonarLintConsole.get(project).error("Cannot get rule description", error)
                                ruleDetails = null
                            } else {
                                ruleDetails = response.details()
                            }
                            runOnUiThread(project) {
                                updateUiComponents()
                            }
                        }
                }
            })
        }

    }

    fun clear() {
        finding = null
        ruleDetails = null
        ruleDetailsLoader.clearState()
        updateUiComponents()
    }

    fun setSelectedFinding(module: Module, finding: Finding) {
        this.finding = finding
        ruleDetailsLoader.updateActiveRuleDetailsIfNeeded(module, finding)
    }

    private fun updateUiComponents() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val finding = this.finding
        val ruleDetails = this.ruleDetails
        if (finding == null || ruleDetails == null) {
            val errorLoadingRuleDetails = finding != null
            descriptionPanel.removeAll()
            hideRuleParameters()
            ruleNameLabel.text = ""
            headerPanel.showMessage(if (errorLoadingRuleDetails) "Couldn't find the rule description" else "Select a finding to display the rule description")
            securityHotspotHeaderMessage.text = ""
            securityHotspotHeaderMessage.isVisible = false
        } else {
            updateHeader(finding, ruleDetails)
            descriptionPanel.removeAll()
            ruleDetails.description.map(
                { monolithDescription ->
                    val htmlHeader = monolithDescription.htmlContent
                    if (!htmlHeader.isNullOrBlank()) {
                        val htmlViewer = RuleHtmlViewer(true)
                        descriptionPanel.add(htmlViewer, BorderLayout.CENTER)
                        htmlViewer.updateHtml(htmlHeader)
                    }
                },
                { withSections ->
                    val htmlHeader = withSections.introductionHtmlContent
                    if (!htmlHeader.isNullOrBlank()) {
                        val htmlViewer = RuleHtmlViewer(false)
                        descriptionPanel.add(htmlViewer, BorderLayout.NORTH)
                        htmlViewer.updateHtml(htmlHeader)
                    }
                    val sectionsTabs = JBTabbedPane()
                    sectionsTabs.font = UIUtil.getLabelFont().deriveFont(Font.BOLD)

                    withSections.tabs.forEachIndexed { index, tabDesc ->
                        addTab(tabDesc, sectionsTabs, index)
                    }

                    descriptionPanel.add(sectionsTabs, BorderLayout.CENTER)
                })
            updateParams(ruleDetails)
        }
    }

    private fun addTab(tabDesc: ActiveRuleDescriptionTabDto, sectionsTabs: JBTabbedPane, index: Int) {
        val sectionPanel = JBPanel<JBPanel<*>>(BorderLayout())
        val htmlViewer = RuleHtmlViewer(true)
        tabDesc.content.map({ nonContextual -> htmlViewer.updateHtml(nonContextual.htmlContent) }, { contextual ->
            val comboPanel = JBPanel<JBPanel<*>>(HorizontalLayout(JBUI.scale(UIUtil.DEFAULT_HGAP)))
            comboPanel.add(JBLabel("Which component or framework contains the issue?"))
            val contextCombo = ComboBox(DefaultComboBoxModel(contextual.contextualSections.toTypedArray()))
            contextCombo.renderer = SimpleListCellRenderer.create("", ActiveRuleContextualSectionDto::getDisplayName)
            contextCombo.addActionListener {
                val htmlContent = (contextCombo.selectedItem as ActiveRuleContextualSectionDto).htmlContent
                htmlViewer.updateHtml(htmlContent)
            }
            comboPanel.add(contextCombo)
            sectionPanel.add(comboPanel, BorderLayout.NORTH)
            contextCombo.selectedIndex =
                contextual.contextualSections.indexOfFirst { sec -> sec.contextKey == contextual.defaultContextKey }
        })
        sectionPanel.add(htmlViewer, BorderLayout.CENTER)
        sectionsTabs.insertTab(tabDesc.title, null, sectionPanel, null, index)
    }

    private fun updateHeader(finding: Finding, ruleDescription: ActiveRuleDetailsDto) {
        ruleNameLabel.text = ruleDescription.name
        ruleNameLabel.setCopyable(true)
        securityHotspotHeaderMessage.isVisible = finding is LiveSecurityHotspot
        if (finding is LiveSecurityHotspot) {
            val htmlStringBuilder = StringBuilder("""
                A ${externalLink("Security Hotspot", "https://docs.sonarqube.org/latest/user-guide/security-hotspots/")}
                highlights a security-sensitive piece of code that the developer <b>needs to review</b>.
                Upon review, you’ll either find there is no threat or you need to apply a fix to secure the code.
                <br>
                At the moment, the status of a Security Hotspot can only be updated in SonarQube. 
                """.trimIndent())
            val serverFindingKey = finding.serverFindingKey
            if (serverFindingKey != null) {
                val serverConnection =
                    SonarLintUtils.getService(project, ProjectBindingManager::class.java).serverConnection
                val projectKey = Settings.getSettingsFor(project).projectKey
                if (projectKey != null) {
                    htmlStringBuilder.append(
                        """
                        Click ${externalLink("here", "${serverConnection.hostUrl}/security_hotspots?id=${urlEncode(projectKey)}&hotspots=${urlEncode(serverFindingKey)}")}
                        to open it on '${serverConnection.name}' server.""".trimIndent()
                    )
                }

            }
            SwingHelper.setHtml(securityHotspotHeaderMessage, htmlStringBuilder.toString(), JBUI.CurrentTheme.ContextHelp.FOREGROUND)
            headerPanel.update(ruleDescription.key, ruleDescription.type, finding.vulnerabilityProbability)
        } else {
            headerPanel.update(ruleDescription.key, ruleDescription.type, ruleDescription.severity)
        }
    }

    private fun externalLink(text: String, href: String): String {
        return """<a href="$href">$text<icon src="AllIcons.Ide.External_link_arrow" href="$href"></a>"""
    }

    private fun updateParams(ruleDescription: ActiveRuleDetailsDto) {
        if (ruleDescription.params.isNotEmpty()) {
            populateParamPanel(ruleDescription)
        } else {
            hideRuleParameters()
        }
    }

    private fun hideRuleParameters() {
        paramsPanel.isVisible = false
    }

    private fun populateParamPanel(ruleDetails: ActiveRuleDetailsDto) {
        paramsPanel.isVisible = true
        paramsPanel.apply {
            removeAll()
            val constraints = GridBagConstraints()
            constraints.anchor = GridBagConstraints.BASELINE_LEADING
            constraints.gridy = 0
            for (param in ruleDetails.params) {
                val paramDefaultValue = param.defaultValue
                val defaultValue = paramDefaultValue ?: "(none)"
                val currentValue = Settings.getGlobalSettings().getRuleParamValue(ruleDetails.key, param.name).orElse(defaultValue)
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
                    this, """<small>Parameter values can be set in <a href="$RULE_CONFIG_LINK_PREFIX${ruleDetails.key}">Rule Settings</a>. 
            | In connected mode, server side configuration overrides local settings.</small>""".trimMargin(), UIUtil.getLabelForeground()
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
