package org.sonarlint.intellij.config

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent


class SonarLintNewCodeDefinitionConfigurable : Configurable, Configurable.NoMargin, Configurable.NoScroll {

    private val panel: JBPanel<JBPanel<*>> = JBPanel(GridBagLayout())
    private val fieldInput: JBIntSpinner

    init {
        val newCodeDefinition = getService(CleanAsYouCodeService::class.java).getNewCodeDefinitionDays()
        fieldInput = JBIntSpinner(newCodeDefinition.toInt(), 1, 90)

        val description = JBLabel("<html>Only used when not in connected mode.</html>")
        val fromLabel = JBLabel("From last")
        val daysLabel = JBLabel("days")
        val descriptionB = JBLabel("Maximum: 90 days | Minimum: 1 day")

        val gc = GridBagConstraints(
            0, 0, 3, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
            JBUI.insets(2, 2, 2, 2), 0, 0
        )

        panel.add(description, gc)
        gc.gridy = 1
        gc.gridwidth = 1
        panel.add(fromLabel, gc)
        gc.gridx = 1
        panel.add(fieldInput, gc)
        gc.gridx = 2
        panel.add(daysLabel, gc)
        gc.gridx = 0
        gc.gridy = 2
        gc.gridwidth = 3
        panel.add(descriptionB, gc)
        panel.maximumSize = Dimension(70, 70)
    }

    override fun createComponent(): JComponent {
        return panel
    }

    override fun isModified(): Boolean {
        return getGlobalSettings().newCodeDefinitionDays != fieldInput.number.toLong()
    }

    override fun apply() {
        getService(CleanAsYouCodeService::class.java).setNewCodeDefinition(fieldInput.number.toLong())
    }

    override fun getDisplayName(): String {
        return "Change New Code Definition"
    }

}
