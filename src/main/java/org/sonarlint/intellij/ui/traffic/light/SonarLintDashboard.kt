package org.sonarlint.intellij.ui.traffic.light

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.pluralize
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.persistence.FindingsCache
import javax.swing.JPanel

class SonarLintDashboard(private val editor: Editor) {
    val panel = JPanel(VerticalFlowLayout())
    private val NO_FINDINGS_TEXT = "SonarLint: No findings, keep up the good job!"
    private val findingsSummaryLabel = JBLabel(NO_FINDINGS_TEXT)
//    private val focusOnNewCodeCheckbox = JBCheckBox("Focus on new code")
    private val focusDropDown = DropDownLink("Focus on New Code", listOf("Focus on New Code", "Focus on all issues"))

    init {
        panel.background = UIUtil.getToolTipBackground()
        panel.add(findingsSummaryLabel)
//        focusOnNewCodeCheckbox.isOpaque = false
//        panel.add(focusOnNewCodeCheckbox)
        val actionsPanel = JPanel()
        actionsPanel.add(focusDropDown)
        actionsPanel.background = UIUtil.getToolTipActionBackground()
        panel.add(actionsPanel)
    }

    fun refresh() {
        val project = editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val findings = SonarLintUtils.getService(project, FindingsCache::class.java).getFindingsForFile(file)
        if (findings.isEmpty()) {
            findingsSummaryLabel.text = NO_FINDINGS_TEXT
        } else {
            val issuesCount = findings.filterIsInstance<LiveIssue>().size
            val hotspotsCount = findings.filterIsInstance<LiveSecurityHotspot>().size
            var text = "SonarLint: Found "
            if (issuesCount > 0) {
                text += issuesCount.toString() + pluralize(" issue", issuesCount.toLong())
            }
            if (hotspotsCount > 0) {
                if (issuesCount > 0) {
                    text += " and "
                }
                text += hotspotsCount.toString() + pluralize(" hotspot", hotspotsCount.toLong())
            }
            findingsSummaryLabel.text= text
        }
    }
}
