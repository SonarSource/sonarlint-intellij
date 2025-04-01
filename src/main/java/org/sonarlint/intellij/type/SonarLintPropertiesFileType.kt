package org.sonarlint.intellij.type

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType

class SonarLintPropertiesFileType : FileType {
    override fun getName() = "SonarQube for IDE Properties"
    override fun getDescription() = "SonarQube for IDE property files"
    override fun getDefaultExtension() = "properties"
    override fun getIcon() = AllIcons.FileTypes.Properties
    override fun isBinary() = false
}
