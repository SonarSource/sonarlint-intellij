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

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Stands in for a SonarQube CLI installed by the official script, in the user home the build gives to the backend
 * (see `sonarlint.test.sloop.user.home` in build.gradle.kts). The backend finds it and runs `sonar --version`,
 * `sonar system status` and `sonar system reset` for real. Its state lives in files next to the script, a shell script,
 * or a batch file on Windows.
 */
class FakeSonarQubeCli {

    // On Windows the build points %LOCALAPPDATA% into the backend's user home
    private val installRoot = if (SystemInfo.isWindows) {
        sloopUserHome().resolve("AppData/Local/sonarqube-cli")
    } else {
        sloopUserHome().resolve(".local/share/sonarqube-cli")
    }
    private val binDirectory = installRoot.resolve("bin")
    private val executable = binDirectory.resolve(if (SystemInfo.isWindows) "sonar.cmd" else "sonar")
    private val commandLog = sloopUserHome().resolve("fake-sonar-commands.log")

    /** Arguments of every command the backend ran, oldest first. */
    val receivedCommands: List<String>
        get() = if (commandLog.exists()) commandLog.readLines() else emptyList()

    val isInstalled: Boolean
        get() = executable.exists()

    fun install(authenticated: Boolean = false) {
        Files.createDirectories(binDirectory)
        writeStatus(authenticated, integratedAgentIds = emptyList())
        if (SystemInfo.isWindows) {
            writeWindowsScript()
            return
        }
        Files.writeString(
            executable,
            """
            #!/bin/sh
            dir=${'$'}(dirname "${'$'}0")
            echo "${'$'}*" >> "$commandLog"
            case "${'$'}*" in
              --version) [ -f "${'$'}dir/broken" ] && exit 1; echo "$VERSION" ;;
              "system status"*) cat "${'$'}dir/status.json" ;;
              "system reset --force") ;;
              *) echo "fake sonar: unsupported command ${'$'}*" >&2; exit 1 ;;
            esac
            """.trimIndent() + "\n"
        )
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    private fun writeWindowsScript() {
        Files.writeString(
            executable,
            """
            @echo off
            set "dir=%~dp0"
            echo %*>>"$commandLog"
            if "%~1"=="--version" (
              if exist "%dir%broken" exit /b 1
              echo $VERSION
              exit /b 0
            )
            if "%~1 %~2"=="system status" (
              type "%dir%status.json"
              exit /b 0
            )
            if "%~1 %~2 %~3"=="system reset --force" exit /b 0
            echo fake sonar: unsupported command %* 1>&2
            exit /b 1
            """.trimIndent().replace("\n", "\r\n") + "\r\n"
        )
    }

    /** Makes `sonar --version` fail, as a damaged installation would. */
    fun breakInstallation() {
        Files.writeString(binDirectory.resolve("broken"), "")
    }

    /** What `sonar auth login` would leave behind. */
    fun signIn() {
        writeStatus(authenticated = true, integratedAgentIds = emptyList())
    }

    /** What `sonar integrate <agent>` would leave behind, e.g. "claude-code". */
    fun recordIntegration(agentId: String) {
        writeStatus(authenticated = true, integratedAgentIds = listOf(agentId))
    }

    @OptIn(ExperimentalPathApi::class)
    fun uninstall() {
        installRoot.deleteRecursively()
        commandLog.deleteIfExists()
    }

    private fun writeStatus(authenticated: Boolean, integratedAgentIds: List<String>) {
        val auth = if (authenticated) {
            """{"status":"authenticated","server":"https://sonarcloud.io","org":"$ORGANIZATION","token":"active"}"""
        } else {
            """{"status":"unauthenticated"}"""
        }
        val integrations = integratedAgentIds.joinToString(",") {
            """{"id":"$it","path":"/fake/$it","mcp":{"configured":true,"valid":true},"hooks":{"configured":true,"valid":true}}"""
        }
        Files.writeString(binDirectory.resolve("status.json"), """{"version":"$VERSION","auth":$auth,"integrations":[$integrations]}""")
    }

    companion object {
        /** No real CLI has this version, so the UI showing it proves the backend found this fake. */
        const val VERSION = "0.0.1-fake"
        const val ORGANIZATION = "fake-org"
    }
}

private const val SLOOP_USER_HOME_PROPERTY = "sonarlint.test.sloop.user.home"

/** False when the build could not give the backend its own user home, e.g. from a path with spaces. */
fun isSloopUserHomeIsolated(): Boolean = System.getProperty(SLOOP_USER_HOME_PROPERTY) != null

private fun sloopUserHome(): Path = Path.of(
    checkNotNull(System.getProperty(SLOOP_USER_HOME_PROPERTY)) { "Backend user home is not configured" }
)
