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
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsBySeverity
import org.sonarlint.intellij.SonarLintIcons.backgroundColorsByVulnerabilityProbability
import org.sonarlint.intellij.SonarLintIcons.borderColorsByImpact
import org.sonarlint.intellij.SonarLintIcons.borderColorsBySeverity
import org.sonarlint.intellij.SonarLintIcons.borderColorsByVulnerabilityProbability
import org.sonarlint.intellij.actions.MarkAsResolvedAction.Companion.canBeMarkedAsResolved
import org.sonarlint.intellij.actions.MarkAsResolvedAction.Companion.openMarkAsResolvedDialogAsync
import org.sonarlint.intellij.actions.ReopenIssueAction.Companion.canBeReopened
import org.sonarlint.intellij.actions.ReopenIssueAction.Companion.reopenIssueDialog
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CLEAN_CODE_LINK
import org.sonarlint.intellij.finding.Issue
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.util.RoundedPanelWithBackgroundColor
import org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.client.utils.SoftwareQuality
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.EffectiveIssueDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.MQRModeDetails
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails


class RuleHeaderPanel(private val parent: Disposable) : JBPanel<RuleHeaderPanel>(BorderLayout()) {
    companion object {
        private const val MARK_AS_RESOLVED = "Mark Issue as\u2026"
        private const val REOPEN = "Reopen"
    }

    private val wrappedPanel = JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.LEFT))
    private val attributePanel = RoundedPanelWithBackgroundColor(JBColor(Gray._236, Gray._72))
    private val qualityLabels = LinkedList<RoundedPanelWithBackgroundColor>()
    private var severityLabel: RoundedPanelWithBackgroundColor? = null
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
        severityLabel = null
        ruleKeyLabel.text = ""
        hotspotVulnerabilityLabel.isVisible = false
        hotspotVulnerabilityValueLabel.text = ""
        hotspotVulnerabilityValueLabel.border = BorderFactory.createEmptyBorder()
        changeStatusButton.isVisible = false
        wrappedPanel.removeAll()
        removeAll()
        repaint()
    }

    fun updateForRuleConfiguration(
        cleanCodeAttribute: CleanCodeAttribute,
        impacts: List<ImpactDto>,
        ruleKey: String
    ) {
        updateCommonFieldsInMQRMode(cleanCodeAttribute, impacts, ruleKey)
    }

    fun updateForIssue(project: Project, severityDetails: Either<StandardModeDetails, MQRModeDetails>, ruleKey: String, issue: Issue) {
        updateCommonFields(severityDetails, ruleKey)

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

    fun updateForServerIssue(ruleDetails: EffectiveRuleDetailsDto) {
        updateCommonFields(ruleDetails.severityDetails, ruleDetails.key)
    }

    fun updateForServerIssue(issueDetails: EffectiveIssueDetailsDto) {
        updateCommonFields(issueDetails.severityDetails, issueDetails.ruleKey)
    }

    fun updateForSecurityHotspot(project: Project, ruleKey: String, securityHotspot: LiveSecurityHotspot) {
        clear()
        severityLabel =
            RoundedPanelWithBackgroundColor(
                backgroundColorsByVulnerabilityProbability[securityHotspot.vulnerabilityProbability],
                borderColorsByVulnerabilityProbability[securityHotspot.vulnerabilityProbability]
            ).apply {
                add(JBLabel().apply { icon = SonarLintIcons.hotspotTypeWithProbability(securityHotspot.vulnerabilityProbability) })
                add(JBLabel(cleanCapitalized(RuleType.SECURITY_HOTSPOT.toString())).apply {
                    foreground = SonarLintIcons.fontColorsByVulnerabilityProbability[securityHotspot.vulnerabilityProbability]
                })
            }
        ruleKeyLabel.text = ruleKey
        ruleKeyLabel.setCopyable(true)
        organizeHeader(false)
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

    private fun updateCommonFields(severityDetails: Either<StandardModeDetails, MQRModeDetails>, ruleKey: String) {
        if (severityDetails.isLeft) {
            val mqrMode = severityDetails.left
            updateCommonFieldsInStandardMode(mqrMode.severity, mqrMode.type, ruleKey)
        } else {
            val standardMode = severityDetails.right
            updateCommonFieldsInMQRMode(
                CleanCodeAttribute.fromDto(standardMode.cleanCodeAttribute),
                standardMode.impacts,
                ruleKey
            )
        }
    }

    private fun updateCommonFieldsInStandardMode(
        severity: IssueSeverity,
        type: RuleType,
        ruleKey: String
    ) {
        clear()
        severityLabel = RoundedPanelWithBackgroundColor(backgroundColorsBySeverity[severity], borderColorsBySeverity[severity]).apply {
            add(JBLabel().apply { icon = SonarLintIcons.getIconForTypeAndSeverity(type, severity) })
            add(JBLabel(cleanCapitalized(type.toString())).apply {
                foreground = SonarLintIcons.fontColorsBySeverity[severity]
            })
            add(JBLabel().apply { icon = SonarLintIcons.severity(severity) })
        }
        ruleKeyLabel.text = ruleKey
        ruleKeyLabel.setCopyable(true)

        organizeHeader(false)
    }

    private fun updateCommonFieldsInMQRMode(
        cleanCodeAttribute: CleanCodeAttribute,
        impacts: List<ImpactDto>,
        ruleKey: String
    ) {
        clear()
        val attributeLabel =
            JBLabel("<html><b>" + cleanCapitalized(cleanCodeAttribute.category.label) + " issue</b> | " + cleanCodeAttribute.label + "<br></html>")
        attributePanel.apply {
            add(attributeLabel)
            toolTipText = "Code attributes are characteristics that, when followed, ensure strong code quality and security."
        }
        impacts.forEach {
            val impactSeverity = ImpactSeverity.fromDto(it.impactSeverity)
            val cleanImpact = impactSeverity.label
            val cleanQuality = SoftwareQuality.fromDto(it.softwareQuality).label
            val qualityPanel =
                RoundedPanelWithBackgroundColor(backgroundColorsByImpact[impactSeverity], borderColorsByImpact[impactSeverity]).apply {
                    toolTipText = "Issues found for this rule will have a $cleanImpact impact on the $cleanQuality of your software."
                }
            qualityPanel.add(JBLabel(cleanCapitalized(it.softwareQuality.toString())).apply {
                foreground = SonarLintIcons.fontColorsByImpact[impactSeverity]
            })
            qualityPanel.add(JBLabel().apply { icon = SonarLintIcons.impact(impactSeverity) })
            qualityLabels.add(qualityPanel)
        }
        ruleKeyLabel.text = ruleKey
        ruleKeyLabel.setCopyable(true)

        organizeHeader(true)
    }

    private fun organizeHeader(isMQRMode: Boolean) {
        if (isMQRMode) {
            wrappedPanel.add(attributePanel.apply { border = BorderFactory.createEmptyBorder(0, 0, 0, 15) })
            qualityLabels.forEach { wrappedPanel.add(it) }
        } else {
            wrappedPanel.add(severityLabel)
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
        if (isMQRMode) {
            wrappedPanel.add(learnMore)
        }
        add(wrappedPanel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun cleanCapitalized(txt: String): String {
        return StringUtil.capitalizeWords(clean(txt), true)
    }

    private fun clean(txt: String): String {
        return txt.lowercase().replace("_", " ")
    }

}
