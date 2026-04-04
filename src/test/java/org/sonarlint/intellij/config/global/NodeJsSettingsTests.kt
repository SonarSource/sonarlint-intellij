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

import com.intellij.openapi.project.Project
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.sonarlint.intellij.common.nodejs.NodeJsProvider

class NodeJsSettingsTests {

    @Test
    fun firstNonNullNodePath_skips_first_project_when_no_local_node() {
        val pNoNode = mock<Project>()
        val pWithNode = mock<Project>()
        val provider = NodeJsProvider { project ->
            when (project) {
                pNoNode -> null
                pWithNode -> Paths.get("/opt/node22/bin/node")
                else -> null
            }
        }

        val result = NodeJsSettings.firstNonNullNodePathAmongProjects(arrayOf(pNoNode, pWithNode), listOf(provider))

        assertThat(result).isEqualTo(Paths.get("/opt/node22/bin/node"))
    }

    @Test
    fun firstNonNullNodePath_returns_null_when_no_project_has_node() {
        val p1 = mock<Project>()
        val p2 = mock<Project>()
        val provider = NodeJsProvider { null }

        val result = NodeJsSettings.firstNonNullNodePathAmongProjects(arrayOf(p1, p2), listOf(provider))

        assertThat(result).isNull()
    }

    @Test
    fun firstNonNullNodePath_prefers_earliest_project_with_node() {
        val p1 = mock<Project>()
        val p2 = mock<Project>()
        val provider = NodeJsProvider { project ->
            when (project) {
                p1 -> Paths.get("/v18/node")
                p2 -> Paths.get("/v22/node")
                else -> null
            }
        }

        val result = NodeJsSettings.firstNonNullNodePathAmongProjects(arrayOf(p1, p2), listOf(provider))

        assertThat(result).isEqualTo(Paths.get("/v18/node"))
    }

}
