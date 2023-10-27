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
package org.sonarlint.intellij.core

import javax.swing.Icon
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.common.util.SonarLintUtils

enum class SonarProduct(val productName: String, val icon: Icon, val documentation: ServerProductDocumentation) {
    SONARQUBE("SonarQube", SonarLintIcons.ICON_SONARQUBE_16, SonarQubeDocumentation),
    SONARCLOUD("SonarCloud", SonarLintIcons.ICON_SONARCLOUD_16, SonarCloudDocumentation);
    companion object {
        @JvmStatic
        fun fromUrl(serverUrl: String) = if (SonarLintUtils.isSonarCloudAlias(serverUrl)) SONARCLOUD else SONARQUBE
    }
}