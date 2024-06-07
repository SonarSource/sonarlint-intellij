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
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.Gray
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.util.LinkedList
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.MarkAsResolvedAction.Companion.canBeMarkedAsResolved
import org.sonarlint.intellij.actions.MarkAsResolvedAction.Companion.openMarkAsResolvedDialogAsync
import org.sonarlint.intellij.actions.ReopenIssueAction.Companion.canBeReopened
import org.sonarlint.intellij.actions.ReopenIssueAction.Companion.reopenIssueDialog
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CLEAN_CODE_LINK
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarlint.intellij.util.SonarGotItTooltipsUtils
import org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.client.utils.SoftwareQuality
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType


class RuleHeaderPanel(private val parent: Disposable) : JBPanel<RuleHeaderPanel>(BorderLayout()) {
    companion object {
        private const val MARK_AS_RESOLVED = "Mark Issue as\u2026"
        private const val REOPEN = "Reopen"
    }

    private val wrappedPanel = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.LEFT))
    private val attributePanel = RoundedPanelWithBackgroundColor(JBColor(Gray._236, Gray._72))
    private val qualityLabels = LinkedList<RoundedPanelWithBackgroundColor>()
    private val ruleTypeIcon = JBLabel()
    private val ruleTypeLabel = JBLabel()
    private val ruleSeverityIcon = JBLabel()
    private val ruleSeverityLabel = JBLabel()
    private val hotspotVulnerabilityLabel = JBLabel("Review priority: ")
    private val hotspotVulnerabilityValueLabel = JBLabel()
    private val ruleKeyLabel = JBLabel()
    private val changeStatusButton = JButton()
    private val learnMore = HyperlinkLabel("Learn more")
    private val disposableFlag = Disposer.newCheckedDisposable()

    init {
        learnMore.addHyperlinkListener { BrowserUtil.browse(CLEAN_CODE_LINK) }
        Disposer.register(parent, disposableFlag)
    }

    fun clear() {
        attributePanel.removeAll()
        qualityLabels.clear()
        ruleTypeIcon.icon = null
        ruleTypeLabel.text = ""
        ruleKeyLabel.text = ""
        ruleSeverityIcon.icon = null
        ruleSeverityLabel.text = ""
        hotspotVulnerabilityLabel.isVisible = false
        hotspotVulnerabilityValueLabel.text = ""
        hotspotVulnerabilityValueLabel.border = BorderFactory.createEmptyBorder()
        changeStatusButton.isVisible = false
        wrappedPanel.removeAll()
        removeAll()
        repaint()
    }

    fun updateForRuleConfiguration(
        ruleKey: String, type: RuleType, severity: IssueSeverity,
        attribute: CleanCodeAttribute?, qualities: List<ImpactDto>,
    ) {
        clear()
        updateServerCommonFields(type, attribute, qualities, ruleKey)
        updateRuleSeverity(severity)
    }

    fun updateForIssue(project: Project, type: RuleType, severity: IssueSeverity, issue: Issue) {
        clear()
        updateCommonFields(type, issue.getCleanCodeAttribute(), issue.getImpacts(), issue.getRuleKey())
        updateRuleSeverity(severity)

        if (canBeReopened(project, issue)) {
            changeStatusButton.isVisible = true
            changeStatusButton.action = object : AbstractAction(REOPEN) {
                override fun actionPerformed(e: ActionEvent?) {
                    reopenIssueDialog(project, issue)
                }
            }
        } else if (canBeMarkedAsResolved(project, issue)) {
            changeStatusButton.isVisible = true
            changeStatusButton.action = object : AbstractAction(MARK_AS_RESOLVED) {
                override fun actionPerformed(e: ActionEvent?) {
                    openMarkAsResolvedDialogAsync(project, issue)
                }
            }
        }
    }

    fun updateForServerIssue(ruleDescription: EffectiveRuleDetailsDto, ruleKey: String) {
        clear()
        updateServerCommonFields(ruleDescription.type, ruleDescription.cleanCodeAttribute?.let { CleanCodeAttribute.fromDto(it) }, ruleDescription.defaultImpacts, ruleKey)
        updateRuleSeverity(ruleDescription.severity)
    }

    private fun updateRuleSeverity(severity: IssueSeverity) {
        ruleSeverityIcon.icon = SonarLintIcons.severity(severity)
        ruleSeverityLabel.text = cleanCapitalized(severity.toString())
        ruleSeverityLabel.setCopyable(true)
    }

    fun updateForSecurityHotspot(project: Project, ruleKey: String, type: RuleType, securityHotspot: LiveSecurityHotspot) {
        clear()
        updateCommonFields(type, securityHotspot.getCleanCodeAttribute(), securityHotspot.getImpacts(), ruleKey)
        ruleTypeIcon.icon = SonarLintIcons.hotspotTypeWithProbability(securityHotspot.vulnerabilityProbability)
        hotspotVulnerabilityLabel.isVisible = true
        hotspotVulnerabilityValueLabel.apply {
            text = securityHotspot.vulnerabilityProbability.name
            setCopyable(true)
            border = BorderFactory.createEmptyBorder(0, 0, 0, 15)
        }

        securityHotspot.getServerKey()?.let {
            changeStatusButton.action = object : AbstractAction("Change Status") {
                override fun actionPerformed(e: ActionEvent?) {
                    ReviewSecurityHotspotAction(it, securityHotspot.status).openReviewingDialogAsync(project, securityHotspot.file())
                }
            }
            changeStatusButton.isVisible = securityHotspot.isValid()
        }
    }

    private fun updateServerCommonFields(type: RuleType, attribute: CleanCodeAttribute?, qualities: List<ImpactDto>, ruleKey: String) {
        val newCctEnabled = attribute != null && qualities.isNotEmpty()
        if (newCctEnabled) {
            val attributeLabel = JBLabel("<html><b>" + cleanCapitalized(attribute!!.label) + " issue</b> | Not " + clean(attribute.toString()) + "<br></html>")
            attributePanel.apply {
                add(attributeLabel)
                toolTipText = "Clean Code attributes are characteristics code needs to have to be considered clean."
            }
            qualities.forEach {
                val impactSeverity = ImpactSeverity.fromDto(it.impactSeverity)
                val cleanImpact = impactSeverity.label
                val cleanQuality = SoftwareQuality.fromDto(it.softwareQuality).label
                val qualityPanel = RoundedPanelWithBackgroundColor(SonarLintIcons.backgroundColorsByImpact[impactSeverity]).apply {
                    toolTipText = "Issues found for this rule will have a $cleanImpact impact on the $cleanQuality of your software."
                }
                qualityPanel.add(JBLabel(cleanCapitalized(it.softwareQuality.toString())).apply {
                    foreground = SonarLintIcons.fontColorsByImpact[impactSeverity]
                })
                qualityPanel.add(JBLabel().apply { icon = SonarLintIcons.impact(impactSeverity) })
                qualityLabels.add(qualityPanel)
            }
        } else {
            ruleTypeIcon.icon = SonarLintIcons.type(type)
            ruleTypeLabel.text = cleanCapitalized(type.toString())
            ruleTypeLabel.setCopyable(true)
        }
        ruleKeyLabel.text = ruleKey
        ruleKeyLabel.setCopyable(true)

        organizeHeader(newCctEnabled)
    }

    private fun updateCommonFields(type: RuleType, attribute: CleanCodeAttribute?, qualities: Map<SoftwareQuality, ImpactSeverity>, ruleKey: String) {
        val newCctEnabled = attribute != null && qualities.isNotEmpty()
        if (newCctEnabled) {
            val attributeLabel = JBLabel("<html><b>" + cleanCapitalized(attribute!!.category.label) + " issue</b> | " + clean(attribute.label) + "<br></html>")
            attributePanel.apply {
                add(attributeLabel)
                toolTipText = "Clean Code attributes are characteristics code needs to have to be considered clean."
            }
            qualities.entries.forEach {
                val cleanImpact = cleanCapitalized(it.value.label)
                val cleanQuality = cleanCapitalized(it.key.label)
                val qualityPanel = RoundedPanelWithBackgroundColor(SonarLintIcons.backgroundColorsByImpact[it.value]).apply {
                    toolTipText = "Issues found for this rule will have a $cleanImpact impact on the $cleanQuality of your software."
                }
                qualityPanel.add(JBLabel(cleanCapitalized(it.key.toString())).apply {
                    foreground = SonarLintIcons.fontColorsByImpact[it.value]
                })
                qualityPanel.add(JBLabel().apply { icon = SonarLintIcons.impact(it.value) })
                qualityLabels.add(qualityPanel)
            }
        } else {
            ruleTypeIcon.icon = SonarLintIcons.type(type)
            ruleTypeLabel.text = cleanCapitalized(type.toString())
            ruleTypeLabel.setCopyable(true)
        }
        ruleKeyLabel.text = ruleKey
        ruleKeyLabel.setCopyable(true)

        organizeHeader(newCctEnabled)
    }

    private fun organizeHeader(newCct: Boolean) {
        if (newCct) {
            wrappedPanel.add(attributePanel.apply { border = BorderFactory.createEmptyBorder(0, 0, 0, 15) })
            qualityLabels.forEach { wrappedPanel.add(it) }

            if (!disposableFlag.isDisposed) {
                SonarGotItTooltipsUtils.showCleanCodeToolTip(wrappedPanel, parent)
            }
        } else {
            wrappedPanel.add(ruleTypeIcon)
            wrappedPanel.add(ruleTypeLabel.apply {
                border = JBUI.Borders.emptyRight(0)
            })
            wrappedPanel.add(ruleSeverityIcon)
            wrappedPanel.add(ruleSeverityLabel)
            wrappedPanel.add(hotspotVulnerabilityLabel)
            wrappedPanel.add(hotspotVulnerabilityValueLabel.apply {
                font = JBFont.label().asBold()
                verticalTextPosition = SwingConstants.CENTER
                isOpaque = true
            })
        }
        wrappedPanel.add(ruleKeyLabel.apply {
            border = JBUI.Borders.emptyLeft(10)
        }, HorizontalLayout.CENTER)
        val changeStatusPanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
        changeStatusPanel.apply { border = BorderFactory.createEmptyBorder(0, 15, 0, 0) }

        changeStatusPanel.add(changeStatusButton)
        wrappedPanel.add(changeStatusPanel)
        if (newCct) {
            wrappedPanel.add(learnMore)
        }
        add(wrappedPanel, BorderLayout.CENTER)
    }

    fun showMessage(msg: String) {
        clear()
        ruleTypeLabel.text = msg
    }

    private fun cleanCapitalized(txt: String): String {
        return StringUtil.capitalizeWords(clean(txt), true)
    }

    private fun clean(txt: String): String {
        return txt.lowercase().replace("_", " ")
    }

}
