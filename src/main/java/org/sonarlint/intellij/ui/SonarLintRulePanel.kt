/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
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
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret
import org.apache.commons.text.StringEscapeUtils
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.SECURITY_HOTSPOTS_LINK
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.ruledescription.RuleDescriptionPanel
import org.sonarlint.intellij.ui.ruledescription.RuleHeaderPanel
import org.sonarlint.intellij.ui.ruledescription.RuleLanguages
import org.sonarlint.intellij.util.UrlBuilder
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.EffectiveIssueDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleParamDto


private const val RULE_CONFIG_LINK_PREFIX = "#rule:"
private const val LOADING_TEXT = "Loading rule description\u2026"

class SonarLintRulePanel(private val project: Project, parent: Disposable) : JBLoadingPanel(BorderLayout(), parent) {

    private val mainPanel = JBPanelWithEmptyText(BorderLayout())
    private val topPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val descriptionPanel = RuleDescriptionPanel(project, parent)
    private val ruleNameLabel = JBLabel()
    private val headerPanel = RuleHeaderPanel(parent)
    private val paramsPanel = JBPanel<JBPanel<*>>(GridBagLayout())
    private val securityHotspotHeaderMessage = JEditorPane()
    private val ruleDetailsLoader = RuleDetailsLoader()
    private var finding: Finding? = null
    private var ruleKey: String? = null
    private var issueDetails: EffectiveIssueDetailsDto? = null
    private var ruleDetails: EffectiveRuleDetailsDto? = null
    private var issueNotFoundError: Boolean = false

    init {
        mainPanel.add(topPanel.apply {
            add(ruleNameLabel.apply {
                font = JBFont.h3().asBold()
            }, BorderLayout.NORTH)
            add(headerPanel, BorderLayout.CENTER)
            add(securityHotspotHeaderMessage.apply {
                contentType = UIUtil.HTML_MIME
                if (caret == null) {
                    caret = DefaultCaret()
                }
                (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
                editorKit = HTMLEditorKitBuilder.simple()
                border = JBUI.Borders.empty()
                isEditable = false
                isOpaque = false
                isFocusable = false

                addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
            }, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)

        mainPanel.add(descriptionPanel, BorderLayout.CENTER)

        mainPanel.add(paramsPanel.apply {
            border = IdeBorderFactory.createTitledBorder("Parameters")
        }, BorderLayout.SOUTH)

        add(mainPanel)
        setLoadingText(LOADING_TEXT)
        clear()

        ApplicationManager.getApplication().messageBus.connect(parent)
            .subscribe(
                LafManagerListener.TOPIC,
                LafManagerListener { runOnUiThread(project) { updateUiComponents() } })
    }

    private data class RuleDetailsLoaderState(
        val lastModule: Module?,
        val lastIssueId: UUID?,
        val ruleKey: String?
    )

    /**
     * To avoid useless calls to the backend/server, we cache parameters that may produce different rule details.
     * If the state has not changed, we don't reload rule details.
     */
    inner class RuleDetailsLoader {

        private var state = RuleDetailsLoaderState(null, null, null)

        fun clearState() {
            state = RuleDetailsLoaderState(null, null, null)
        }

        fun updateActiveRuleDetailsIfNeeded(module: Module, ruleKey: String) {
            issueDetails = null
            val newState = RuleDetailsLoaderState(module, null, ruleKey)
            if (state == newState) {
                // Still force a refresh of the UI, as some other fields of the finding may be different
                runOnUiThread(project) {
                    updateUiComponents()
                }
                return
            }
            state = newState
            startLoading()
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, LOADING_TEXT, false) {
                override fun run(progressIndicator: ProgressIndicator) {
                    runOnPooledThread(project) {
                        SonarLintUtils.getService(BackendService::class.java)
                            .getEffectiveRuleDetails(module, ruleKey, null)
                            .orTimeout(30, TimeUnit.SECONDS)
                            .handle { response, error ->
                                stopLoading()
                                ruleDetails = if (error != null) {
                                    SonarLintConsole.get(project).error("Cannot get rule description", error)
                                    null
                                } else {
                                    response.details()
                                }
                                runOnUiThread(project) {
                                    updateUiComponents()
                                }
                            }
                    }
                }
            })
        }

        fun updateActiveIssueDetailsIfNeeded(module: Module, issueId: UUID, openOnCodeFixTab: Boolean) {
            ruleDetails = null
            issueNotFoundError = false
            val newState = RuleDetailsLoaderState(module, issueId, null)
            if (state == newState) {
                refreshUi(openOnCodeFixTab)
                return
            }
            state = newState
            startLoading()
            loadIssueDetails(module, issueId, openOnCodeFixTab)
        }

        private fun refreshUi(openOnCodeFixTab: Boolean) {
            runOnUiThread(project) {
                updateUiComponents()
                if (openOnCodeFixTab) {
                    descriptionPanel.openCodeFixTabAndGenerate()
                }
            }
        }

        private fun loadIssueDetails(module: Module, issueId: UUID, openOnCodeFixTab: Boolean) {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, LOADING_TEXT, false) {
                override fun run(progressIndicator: ProgressIndicator) {
                    runOnPooledThread(project) {
                        SonarLintUtils.getService(BackendService::class.java)
                            .getEffectiveIssueDetails(module, issueId)
                            .orTimeout(30, TimeUnit.SECONDS)
                            .handle { response, error ->
                                if (error != null) {
                                    handleIssueDetailsError(module, error, openOnCodeFixTab)
                                } else {
                                    handleIssueDetailsSuccess(response.details, openOnCodeFixTab)
                                }
                            }
                    }
                }
            })
        }

        private fun handleIssueDetailsError(module: Module, error: Throwable, openOnCodeFixTab: Boolean) {
            val isIssueNotFound = isIssueNotFoundError(error)
            
            if (isIssueNotFound && finding != null) {
                tryFallbackToRuleDetails(module, openOnCodeFixTab)
            } else {
                handleErrorWithoutFallback(error, isIssueNotFound, openOnCodeFixTab)
            }
        }

        private fun isIssueNotFoundError(error: Throwable): Boolean {
            return error.cause is ResponseErrorException &&
                (error.cause as ResponseErrorException).responseError.code == SonarLintRpcErrorCode.ISSUE_NOT_FOUND
        }

        private fun tryFallbackToRuleDetails(module: Module, openOnCodeFixTab: Boolean) {
            issueNotFoundError = true
            val findingRuleKey = finding?.getRuleKey()
            val contextKey = finding?.getRuleDescriptionContextKey()
            
            if (findingRuleKey != null) {
                loadRuleDetailsFallback(module, findingRuleKey, contextKey, openOnCodeFixTab)
            } else {
                handleErrorWithoutFallback(null, true, openOnCodeFixTab)
            }
        }

        private fun loadRuleDetailsFallback(module: Module, ruleKey: String, contextKey: String?, openOnCodeFixTab: Boolean) {
            SonarLintUtils.getService(BackendService::class.java)
                .getEffectiveRuleDetails(module, ruleKey, contextKey)
                .orTimeout(30, TimeUnit.SECONDS)
                .handle { ruleResponse, ruleError ->
                    stopLoading()
                    if (ruleError != null) {
                        SonarLintConsole.get(project).error("Cannot get rule description", ruleError)
                        ruleDetails = null
                        handleErrorWithoutFallback(ruleError, true, openOnCodeFixTab)
                    } else {
                        issueNotFoundError = false // Reset flag on successful fallback
                        ruleDetails = ruleResponse.details()
                        issueDetails = null
                        refreshUi(openOnCodeFixTab)
                    }
                }
        }

        private fun handleErrorWithoutFallback(error: Throwable?, isIssueNotFound: Boolean, openOnCodeFixTab: Boolean) {
            stopLoading()
            if (error != null) {
                SonarLintConsole.get(project).error("Cannot get rule description", error)
            }
            issueDetails = null
            issueNotFoundError = isIssueNotFound
            refreshUi(openOnCodeFixTab)
        }

        private fun handleIssueDetailsSuccess(details: EffectiveIssueDetailsDto, openOnCodeFixTab: Boolean) {
            stopLoading()
            issueDetails = details
            issueNotFoundError = false
            refreshUi(openOnCodeFixTab)
        }
    }

    fun clear() {
        clearValues()
        runOnUiThread(project) { updateUiComponents() }
    }

    private fun clearValues() {
        finding = null
        ruleKey = null
        issueDetails = null
        ruleDetails = null
        issueNotFoundError = false
        ruleDetailsLoader.clearState()
    }

    fun setSelectedFinding(module: Module, ruleKey: String) {
        this.ruleKey = ruleKey
        ruleDetailsLoader.updateActiveRuleDetailsIfNeeded(module, ruleKey)
    }

    fun setSelectedFinding(module: Module, finding: Finding?, findingId: UUID, openOnCodeFixTab: Boolean) {
        this.finding = finding
        ruleDetailsLoader.updateActiveIssueDetailsIfNeeded(module, findingId, openOnCodeFixTab)
    }

    private fun updateUiComponents() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val finding = this.finding
        val ruleKey = this.ruleKey
        val issueDetails = this.issueDetails
        val ruleDetails = this.ruleDetails
        if (issueDetails != null && finding != null) {
            handleIssueDescription(issueDetails, finding)
        } else if (ruleKey != null && ruleDetails != null) {
            handleRuleDescription(ruleDetails)
        } else {
            handleDescriptionError()
        }
    }

    private fun handleIssueDescription(actualIssueDetails: EffectiveIssueDetailsDto, actualFinding: Finding) {
        disableEmptyDisplay(true)
        updateHeader(actualFinding, actualIssueDetails)
        descriptionPanel.removeAll()
        val fileType = RuleLanguages.findFileTypeByRuleLanguage(actualIssueDetails.language)
        val file = actualFinding.file()
        actualIssueDetails.description.map({ monolithDescription ->
            if (actualFinding.isAiCodeFixable() && file != null) {
                descriptionPanel.addMonolithWithCodeFix(monolithDescription, fileType, actualFinding.getId(), file)
            } else {
                descriptionPanel.addMonolith(monolithDescription, fileType)
            }
        }, { withSections ->
            if (actualFinding.isAiCodeFixable() && file != null) {
                descriptionPanel.addSectionsWithCodeFix(withSections, fileType, actualFinding.getId(), file)
            } else {
                descriptionPanel.addSections(withSections, fileType)
            }
        })
        updateParams(actualIssueDetails)
    }

    private fun handleRuleDescription(actualRuleDetails: EffectiveRuleDetailsDto) {
        disableEmptyDisplay(true)
        updateHeader(actualRuleDetails)
        descriptionPanel.removeAll()
        val fileType = RuleLanguages.findFileTypeByRuleLanguage(actualRuleDetails.language)
        actualRuleDetails.description.map(
            { monolithDescription -> descriptionPanel.addMonolith(monolithDescription, fileType) },
            { withSections -> descriptionPanel.addSections(withSections, fileType) }
        )
        updateParams(actualRuleDetails)
    }

    private fun handleDescriptionError() {
        val errorLoadingRuleDetails = finding != null
        descriptionPanel.removeAll()
        ruleNameLabel.text = ""
        disableEmptyDisplay(false)
        val errorMessage = when {
            issueNotFoundError -> "Couldn't find the rule description. The issue may no longer exist. Try triggering a new analysis on the file."
            errorLoadingRuleDetails -> "Couldn't find the rule description"
            else -> "Select a finding to display the rule description"
        }
        mainPanel.withEmptyText(errorMessage)
    }

    private fun updateHeader(ruleDetails: EffectiveRuleDetailsDto) {
        setLabelText(ruleDetails.name)
        securityHotspotHeaderMessage.isVisible = finding is LiveSecurityHotspot
        headerPanel.updateForServerIssue(ruleDetails)
    }

    private fun updateHeader(finding: Finding?, issueDetails: EffectiveIssueDetailsDto) {
        setLabelText(issueDetails.name)
        securityHotspotHeaderMessage.isVisible = finding is LiveSecurityHotspot
        when (finding) {
            null -> headerPanel.updateForServerIssue(issueDetails)
            is LiveSecurityHotspot -> {
                val serverConnection = SonarLintUtils.getService(project, ProjectBindingManager::class.java).serverConnection
                val htmlStringBuilder = StringBuilder(
                    """
                A ${securityHotspotsDocLink()} highlights a security-sensitive piece of code that the developer <b>needs to review</b>.
                Upon review, you’ll either find there is no threat or you need to apply a fix to secure the code.
                """.trimIndent()
                )
                val serverFindingKey = finding.getServerKey()
                if (serverFindingKey != null) {
                    val projectKey = Settings.getSettingsFor(project).projectKey
                    if (projectKey != null) {
                        htmlStringBuilder.append(
                            """
                         Click ${
                                externalLink(
                                    "here",
                                    securityHotspotDetailsLink(serverConnection, projectKey, serverFindingKey)
                                )
                            }
                        to open it on '${serverConnection.name}' server.""".trimIndent()
                        )
                    }

                }
                SwingHelper.setHtml(securityHotspotHeaderMessage, htmlStringBuilder.toString(), JBUI.CurrentTheme.ContextHelp.FOREGROUND)
                headerPanel.updateForSecurityHotspot(project, issueDetails.ruleKey, finding)
            }

            else -> headerPanel.updateForIssue(project, issueDetails.severityDetails, issueDetails.ruleKey, finding as Issue, issueDetails.name)
        }
    }

    private fun setLabelText(input: String?) {
        ruleNameLabel.apply {
            text = StringEscapeUtils.escapeHtml4(input)
            setCopyable(true)
            setAllowAutoWrapping(true)
        }
    }

    private fun securityHotspotDetailsLink(
        serverConnection: ServerConnection,
        projectKey: String,
        serverFindingKey: String,
    ): String {
        val prefixPath = if (serverConnection.isSonarCloud) "project/" else ""
        val path = "${serverConnection.hostUrl}/${prefixPath}security_hotspots"
        return UrlBuilder(path)
            .addParam("id", projectKey)
            .addParam("hotspots", serverFindingKey)
            .build()
    }

    private fun securityHotspotsDocLink() = externalLink("Security Hotspot", SECURITY_HOTSPOTS_LINK)

    private fun externalLink(text: String, href: String): String {
        return """<a href="$href">$text<icon src="AllIcons.Ide.External_link_arrow" href="$href"></a>"""
    }

    private fun updateParams(issueDetails: EffectiveIssueDetailsDto) {
        if (issueDetails.params.isNotEmpty()) {
            populateParamPanel(issueDetails.ruleKey, issueDetails.params)
        } else {
            paramsPanel.isVisible = false
        }
    }

    private fun updateParams(ruleDetails: EffectiveRuleDetailsDto) {
        if (ruleDetails.params.isNotEmpty()) {
            populateParamPanel(ruleDetails.key, ruleDetails.params)
        } else {
            paramsPanel.isVisible = false
        }
    }

    private fun populateParamPanel(ruleKey: String, params: Collection<EffectiveRuleParamDto>) {
        paramsPanel.isVisible = true
        paramsPanel.apply {
            removeAll()
            val constraints = GridBagConstraints()
            constraints.anchor = GridBagConstraints.BASELINE_LEADING
            constraints.gridy = 0
            for (param in params) {
                val paramDefaultValue = param.defaultValue
                val defaultValue = paramDefaultValue ?: "(none)"
                val currentValue = Settings.getGlobalSettings().getRuleParamValue(ruleKey, param.name).orElse(defaultValue)
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
                if (caret == null) {
                    caret = DefaultCaret()
                }
                (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
                editorKit = HTMLEditorKitBuilder.simple()
                addHyperlinkListener(RuleConfigHyperLinkListener(project))
                isEditable = false
                isOpaque = false
                isFocusable = false
                border = null
                SwingHelper.setHtml(
                    this,
                    """<small>Parameter values can be set in <a href="$RULE_CONFIG_LINK_PREFIX$ruleKey">Rule Settings</a>. 
            | In connected mode, server side configuration overrides local settings.</small>""".trimMargin(), UIUtil.getLabelForeground()
                )
            }, constraints)
        }
    }

    private class RuleConfigHyperLinkListener(private val project: Project) : BrowserHyperlinkListener() {

        override fun hyperlinkActivated(e: HyperlinkEvent) {
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

    private fun disableEmptyDisplay(state: Boolean) {
        ruleNameLabel.isVisible = state
        topPanel.isVisible = state
        descriptionPanel.isVisible = state
        paramsPanel.isVisible = state
    }

}
