package org.sonarlint.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent

private const val DOCUMENTATION_URL = "https://github.com/SonarSource/sonarlint-intellij/wiki/Security-Hotspots"

class OpenSecurityHotspotDocumentationAction : AbstractSonarAction(
    "Learn More", "Learn more about security hotspots in SonarLint", AllIcons.Actions.Help
) {

    override fun actionPerformed(e: AnActionEvent) {
        BrowserUtil.browse(DOCUMENTATION_URL)
    }

}
