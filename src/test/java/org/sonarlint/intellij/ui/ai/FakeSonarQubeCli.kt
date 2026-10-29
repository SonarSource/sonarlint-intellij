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
package org.sonarlint.intellij.ui.ai

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.deleteRecursively

/**
 * Stands in for a SonarQube CLI installed by the official script, in the user home the build gives to the backend
 * (see `sonarlint.test.sloop.user.home` in build.gradle.kts). The backend finds it and runs `sonar --version` and
 * `sonar system status` for real.
 */
class FakeSonarQubeCli {

    private val userHome: Path = Path.of(
        checkNotNull(System.getProperty("sonarlint.test.sloop.user.home")) { "Backend user home is not configured" }
    )
    private val installRoot = userHome.resolve(".local/share/sonarqube-cli")

    fun install() {
        val executable = installRoot.resolve("bin/sonar")
        Files.createDirectories(executable.parent)
        Files.writeString(
            executable,
            """
            #!/bin/sh
            case "${'$'}*" in
              --version) echo "$VERSION" ;;
              "system status"*) echo '{"version":"$VERSION","auth":{"status":"unauthenticated"}}' ;;
              *) echo "fake sonar: unsupported command ${'$'}*" >&2; exit 1 ;;
            esac
            """.trimIndent() + "\n"
        )
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun uninstall() {
        installRoot.deleteRecursively()
    }

    companion object {
        /** No real CLI has this version, so the UI showing it proves the backend found this fake. */
        const val VERSION = "0.0.1-fake"
    }
}
