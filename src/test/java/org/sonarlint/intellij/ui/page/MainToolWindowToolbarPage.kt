/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.page

import java.awt.Container
import org.sonarlint.intellij.fixtures.SwingComponentTree

/**
 * Toolbar inside a [com.intellij.openapi.ui.SimpleToolWindowPanel]-based tool window tab (e.g. Findings).
 */
class MainToolWindowToolbarPage internal constructor(
    private val rootProvider: () -> Container,
) {

    val isVisible: Boolean
        get() = SwingComponentTree.walk(rootProvider())
            .any { it.javaClass.simpleName.contains("ActionToolbar", ignoreCase = true) }
}
