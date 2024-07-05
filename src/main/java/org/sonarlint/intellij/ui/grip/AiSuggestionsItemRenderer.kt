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
package org.sonarlint.intellij.ui.grip

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.OffsetIcon
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.scale.JBUIScale.isUsrHiDPI
import java.awt.BorderLayout
import java.awt.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Optional
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JList
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.SonarLintIcons.impact
import org.sonarlint.intellij.SonarLintIcons.type
import org.sonarlint.intellij.common.ui.ReadActionUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.util.CompoundIcon

class AiSuggestionsItemRenderer : DefaultListCellRenderer() {

    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        val component = SimpleColoredComponent()
        if (value is Finding) {
            val project = value.module()?.project ?: return panel
            val inlayHolder = getService(project, InlayHolder::class.java)
            val inlayData = inlayHolder.getInlayData(value.getId())
            val feedbackGiven = inlayData?.feedbackGiven ?: false

            ReadActionUtils.runReadActionSafely(project) {
                val serverConnection = retrieveServerConnection(project)
                val gap = if (isUsrHiDPI) 8 else 4
                val highestImpact = value.getHighestImpact()

                if (value.getCleanCodeAttribute() != null && highestImpact != null) {
                    val impactIcon = impact(highestImpact)
                    if (feedbackGiven) {
                        component.icon = CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, AllIcons.Debugger.Selfreference, impactIcon)
                    } else {
                        val serverIconEmptySpace = SonarLintIcons.ICON_SONARQUBE_16.iconWidth + gap
                        component.icon = OffsetIcon(serverIconEmptySpace, CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, impactIcon))
                    }
                } else {
                    val severity = value.getHighestImpact()
                    var severityIcon: Icon? = null
                    if (severity != null) {
                        severityIcon = impact(severity)
                    }
                    if (severityIcon != null) {
                        val type = value.getType()
                        val typeIcon = type(type)
                        val connection = serverConnection.get()
                        if (feedbackGiven) {
                            component.icon = CompoundIcon(
                                CompoundIcon.Axis.X_AXIS,
                                gap,
                                connection.productIcon,
                                typeIcon,
                                AllIcons.Debugger.Selfreference,
                                severityIcon
                            )
                        } else {
                            val serverIconEmptySpace = SonarLintIcons.ICON_SONARQUBE_16.iconWidth + gap
                            component.icon = OffsetIcon(serverIconEmptySpace, CompoundIcon(CompoundIcon.Axis.X_AXIS, gap, severityIcon))
                        }
                    } else {
                        if (feedbackGiven) {
                            component.icon = CompoundIcon(
                                CompoundIcon.Axis.X_AXIS,
                                gap,
                                AllIcons.Debugger.Selfreference,
                                AllIcons.Debugger.Selfreference
                            )
                        } else {
                            val serverIconEmptySpace = SonarLintIcons.ICON_SONARQUBE_16.iconWidth + gap
                            component.icon = OffsetIcon(serverIconEmptySpace * 2, CompoundIcon(CompoundIcon.Axis.X_AXIS))
                        }
                    }
                }

                if (isSelected) {
                    panel.background = list?.selectionBackground
                    panel.foreground = list?.selectionForeground
                } else {
                    panel.background = list?.background
                    panel.foreground = list?.foreground
                }

                var statusLabel = JBLabel()
                if (inlayData != null) {
                    statusLabel = when (inlayData.status) {
                        AiFindingState.ACCEPTED -> JBLabel(AllIcons.RunConfigurations.ToolbarPassed).apply {
                            toolTipText = "Accepted"
                        }

                        AiFindingState.DECLINED -> JBLabel(AllIcons.Vcs.Remove).apply {
                            toolTipText = "Declined"
                        }

                        AiFindingState.PARTIAL -> JBLabel(AllIcons.General.InspectionsMixed).apply {
                            toolTipText = "Partially Accepted"
                        }

                        AiFindingState.LOADING -> JBLabel(AllIcons.Actions.BuildLoadChanges).apply {
                            toolTipText = "Loading"
                        }

                        AiFindingState.FAILED -> JBLabel(AllIcons.RunConfigurations.ToolbarError).apply {
                            toolTipText = "Failed"
                        }

                        else -> JBLabel()
                    }
                }

                panel.add(statusLabel, BorderLayout.WEST)

                // Add the SimpleColoredComponent to the CENTER of the panel
                panel.add(component, BorderLayout.CENTER)

                renderMessage(component, value)
                renderIntroductionDate(component, inlayData?.generatedDate)
            }
        }
        return panel
    }

    private fun renderMessage(component: SimpleColoredComponent, finding: Finding) {
        finding.file()?.let { component.append("${it.name} - ") }
        if (finding.isValid()) {
            component.toolTipText = "Click to open AI suggestion"
            component.append(finding.getMessage())
        } else {
            this.toolTipText = "Issue is no longer valid"
            component.append(finding.getMessage(), SimpleTextAttributes.GRAY_ATTRIBUTES)
        }
    }

    private fun renderIntroductionDate(component: SimpleColoredComponent, generatedDate: Instant?) {
        component.append(" ")
        val formattedDate = if (generatedDate != null) {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
            formatter.format(generatedDate)
        } else {
            "Unknown"
        }
        component.append(formattedDate, SimpleTextAttributes.GRAY_ATTRIBUTES)
    }

    private fun retrieveServerConnection(project: Project): Optional<ServerConnection> {
        return if (!project.isDisposed) {
            getService(project, ProjectBindingManager::class.java).tryGetServerConnection()
        } else {
            Optional.empty()
        }
    }

}
