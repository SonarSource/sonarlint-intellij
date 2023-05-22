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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.psi.XmlElementFactory
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory.createScrollPane
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
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.ruledescription.RuleCodeSnippet
import org.sonarlint.intellij.ui.ruledescription.RuleHeaderPanel
import org.sonarlint.intellij.ui.ruledescription.RuleHtmlViewer
import org.sonarlint.intellij.ui.ruledescription.RuleLanguages
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleFragment
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleType
import org.sonarlint.intellij.ui.ruledescription.section.HtmlFragment
import org.sonarlint.intellij.ui.ruledescription.section.Section
import org.sonarsource.sonarlint.core.clientapi.backend.rules.EffectiveRuleDetailsDto
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleContextualSectionDto
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleDescriptionTabDto
import org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.swing.DefaultComboBoxModel
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret


private const val RULE_CONFIG_LINK_PREFIX = "#rule:"

private const val PRE_TAG_ENDING = "</pre>"

class SonarLintRulePanel(private val project: Project, private val parent: Disposable) : JBLoadingPanel(BorderLayout(), project) {


    private val descriptionPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val ruleNameLabel = JBLabel()
    private val headerPanel = RuleHeaderPanel()
    private val paramsPanel = JBPanel<JBPanel<*>>(GridBagLayout())
    private val securityHotspotHeaderMessage = JEditorPane()
    private val ruleDetailsLoader = RuleDetailsLoader()
    private var finding: Finding? = null
    private var ruleDetails: EffectiveRuleDetailsDto? = null

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
                    SonarLintUtils.getService(BackendService::class.java)
                        .getActiveRuleDetails(module, finding.getRuleKey(), finding.getRuleDescriptionContextKey())
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
            val fileType = RuleLanguages.findFileTypeByRuleLanguage(ruleDetails.language.languageKey)
            ruleDetails.description.map(
                { monolithDescription ->
                    val scrollPane = parseCodeExamples(monolithDescription.htmlContent, fileType)
                    descriptionPanel.add(scrollPane, BorderLayout.CENTER)
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
                        addTab(tabDesc, sectionsTabs, index, fileType)
                    }

                    descriptionPanel.add(sectionsTabs, BorderLayout.CENTER)
                })
            updateParams(ruleDetails)
        }
    }

    private fun addTab(
        tabDesc: RuleDescriptionTabDto, sectionsTabs: JBTabbedPane, index: Int,
        language: FileType,
    ) {
        val sectionPanel = JBPanel<JBPanel<*>>(BorderLayout())
        tabDesc.content.map({ nonContextual ->
            val scrollPane = parseCodeExamples(nonContextual.htmlContent, language)
            sectionPanel.add(scrollPane, BorderLayout.CENTER)
        }, { contextual ->
            val comboPanel = JBPanel<JBPanel<*>>(HorizontalLayout(JBUI.scale(UIUtil.DEFAULT_HGAP)))
            comboPanel.add(JBLabel("Which component or framework contains the issue?"))
            val contextCombo = ComboBox(DefaultComboBoxModel(contextual.contextualSections.toTypedArray()))
            contextCombo.renderer = SimpleListCellRenderer.create("", RuleContextualSectionDto::getDisplayName)
            contextCombo.addActionListener {
                val layout = sectionPanel.layout as BorderLayout
                layout.getLayoutComponent(BorderLayout.CENTER)?.let { sectionPanel.remove(it) }

                val htmlContent = (contextCombo.selectedItem as RuleContextualSectionDto).htmlContent
                val scrollPane = parseCodeExamples(htmlContent, language)
                sectionPanel.add(scrollPane, BorderLayout.CENTER)
            }
            comboPanel.add(contextCombo)
            sectionPanel.add(comboPanel, BorderLayout.NORTH)
            contextCombo.selectedIndex =
                contextual.contextualSections.indexOfFirst { sec -> sec.contextKey == contextual.defaultContextKey }
        })
        sectionsTabs.insertTab(tabDesc.title, null, sectionPanel, null, index)
    }

    private fun updateHeader(finding: Finding, ruleDescription: EffectiveRuleDetailsDto) {
        ruleNameLabel.text = StringEscapeUtils.escapeHtml(ruleDescription.name)
        ruleNameLabel.setCopyable(true)
        securityHotspotHeaderMessage.isVisible = finding is LiveSecurityHotspot
        if (finding is LiveSecurityHotspot) {
            val serverConnection =
                SonarLintUtils.getService(project, ProjectBindingManager::class.java).serverConnection
            val htmlStringBuilder = StringBuilder(
                """
                A ${securityHotspotsDocLink()} highlights a security-sensitive piece of code that the developer <b>needs to review</b>.
                Upon review, you’ll either find there is no threat or you need to apply a fix to secure the code.
                """.trimIndent()
            )
            val serverFindingKey = finding.serverFindingKey
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
            headerPanel.update(project, serverFindingKey, finding.status, finding.isValid, finding.file, ruleDescription.key, ruleDescription.type, finding.vulnerabilityProbability)
        } else {
            headerPanel.update(ruleDescription.key, ruleDescription.type, ruleDescription.severity)
        }
    }

    private fun securityHotspotDetailsLink(
        serverConnection: ServerConnection,
        projectKey: String,
        serverFindingKey: String,
    ): String {
        val prefixPath = if (serverConnection.isSonarCloud) "project/" else ""
        return "${serverConnection.hostUrl}/{$prefixPath}security_hotspots?id=${urlEncode(projectKey)}&hotspots=${
            urlEncode(
                serverFindingKey
            )
        }"
    }

    private fun securityHotspotsDocLink() = externalLink(
        "Security Hotspot",
        SonarLintDocumentation.SECURITY_HOTSPOTS_LINK
    )

    private fun externalLink(text: String, href: String): String {
        return """<a href="$href">$text<icon src="AllIcons.Ide.External_link_arrow" href="$href"></a>"""
    }

    private fun updateParams(ruleDescription: EffectiveRuleDetailsDto) {
        if (ruleDescription.params.isNotEmpty()) {
            populateParamPanel(ruleDescription)
        } else {
            hideRuleParameters()
        }
    }

    private fun hideRuleParameters() {
        paramsPanel.isVisible = false
    }

    private fun populateParamPanel(ruleDetails: EffectiveRuleDetailsDto) {
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

    private fun isWithinTable(previousHtml: String): Boolean {
        // very naive implementation, but should be good enough
        return StringUtils.countMatches(previousHtml, "<table>") > StringUtils.countMatches(previousHtml, "</table>")
    }

    private fun parseCodeExamples(
        htmlDescription: String, fileType: FileType,
    ): JScrollPane {
        val mainPanel = JBPanel<JBPanel<*>>(VerticalFlowLayout(0, 0))
        var remainingRuleDescription = htmlDescription
        var computedRuleDescription = ""
        var matcherStart: Matcher = Pattern.compile("<pre[^>]*>").matcher(remainingRuleDescription)
        var matcherEnd: Matcher = Pattern.compile(PRE_TAG_ENDING).matcher(remainingRuleDescription)

        val section = Section()
        val xmlElementFactory = XmlElementFactory.getInstance(project)
        while (matcherStart.find() && matcherEnd.find()) {
            val front: String = remainingRuleDescription.substring(0, matcherStart.start()).trim()

            if (front.isNotBlank()) {
                section.mergeOrAdd(HtmlFragment(front))
            }
            computedRuleDescription += front

            val preTag =
                xmlElementFactory.createTagFromText(
                    remainingRuleDescription.substring(matcherStart.start(), matcherStart.end()).trim() + PRE_TAG_ENDING
                )
            val diffId = preTag.getAttributeValue("data-diff-id")
            val diffType = preTag.getAttributeValue("data-diff-type")?.let { CodeExampleType.from(it) }

            val middle: String = remainingRuleDescription.substring(matcherStart.end(), matcherEnd.start()).trim()

            if (middle.isNotBlank()) {
                if (isWithinTable(computedRuleDescription)) {
                    section.mergeOrAdd(HtmlFragment("<pre>$middle$PRE_TAG_ENDING"))
                } else {
                    section.add(CodeExampleFragment(StringEscapeUtils.unescapeHtml(middle), diffType, diffId))
                }
            }
            computedRuleDescription += remainingRuleDescription.substring(matcherStart.start(), matcherEnd.end())
            remainingRuleDescription = remainingRuleDescription.substring(matcherEnd.end(), remainingRuleDescription.length).trim()
            matcherStart = Pattern.compile("<pre[^>]*>").matcher(remainingRuleDescription)
            matcherEnd = Pattern.compile(PRE_TAG_ENDING).matcher(remainingRuleDescription)
        }

        if (remainingRuleDescription.isNotBlank()) {
            section.mergeOrAdd(HtmlFragment(remainingRuleDescription))
        }

        section.fragments.map {
            when (it) {
                is HtmlFragment -> RuleHtmlViewer(false).apply { updateHtml(it.html) }
                is CodeExampleFragment -> RuleCodeSnippet(project, fileType, it).apply {
                    Disposer.register(this@SonarLintRulePanel.parent, this)
                }
            }
        }
            .forEach { mainPanel.add(it) }

        val scrollPane = createScrollPane(mainPanel)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.verticalScrollBar.unitIncrement = 10
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
        scrollPane.border = null

        return scrollPane
    }

}
