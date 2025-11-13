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
package org.sonarlint.intellij.config.global

import com.intellij.openapi.project.ProjectManager
import java.nio.file.Path
import org.sonarlint.intellij.common.nodejs.NodeJsProvider
import org.sonarlint.intellij.config.Settings.getGlobalSettings

data class NodeJsSettings(val path: Path, val version: String) {

    companion object {
        fun getNodeJsPathFromIde(): Path? {
            return ProjectManager.getInstance().openProjects.map { project ->
                NodeJsProvider.EP_NAME.extensionList.firstNotNullOfOrNull { provider -> provider.getNodeJsPathFor(project) }
            }.firstOrNull()
        }

        fun getNodeJsPathForInitialization(): String? {
            return if (!getGlobalSettings().nodejsPath.isNullOrBlank()) {
                getGlobalSettings().nodejsPath
            } else {
                getNodeJsPathFromIde()?.toString()
            }
        }
    }

}
