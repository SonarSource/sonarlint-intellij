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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Stands in for a SonarQube CLI installed by the official script, in the user home the build gives to the backend
 * (see `sonarlint.test.sloop.user.home` in build.gradle.kts). The backend finds it and runs `sonar --version`,
 * `sonar system status` and `sonar system reset` for real, and the IDE terminal runs `sonar auth login` and
 * `sonar integrate`: a one-line launcher runs [FakeSonarQubeCliMain], whose state lives in files next to the launcher.
 */
class FakeSonarQubeCli {

    private val installRoot = sloopUserHome().resolve(".local/share/sonarqube-cli")
    private val binDirectory = installRoot.resolve("bin")
    private val commandLog = sloopUserHome().resolve("fake-sonar-commands.log")

    /** Arguments of every command the backend or the terminal ran, oldest first. */
    val receivedCommands: List<String>
        get() = if (commandLog.exists()) commandLog.readLines() else emptyList()

    val isInstalled: Boolean
        get() = binDirectory.resolve("sonar").exists()

    fun install(authenticated: Boolean = false) {
        Files.createDirectories(binDirectory)
        if (authenticated) {
            Files.writeString(binDirectory.resolve(FakeSonarQubeCliMain.AUTHENTICATION_FILE), ORGANIZATION)
        }
        val executable = binDirectory.resolve("sonar")
        Files.writeString(executable, "#!/bin/sh\nexec ${launchCommand()} \"${'$'}@\"\n")
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    /** Runs [FakeSonarQubeCliMain] on the test JVM's runtime, with the compiled test classes as its only classpath entry. */
    private fun launchCommand(): String {
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        val testClasses = classpathRootOf(FakeSonarQubeCliMain::class.java)
        return listOf(
            java.toString(), "-cp", testClasses.toString(),
            "-D${FakeSonarQubeCliMain.STATE_DIRECTORY_PROPERTY}=$binDirectory",
            "-D${FakeSonarQubeCliMain.COMMAND_LOG_PROPERTY}=$commandLog",
            "-D${FakeSonarQubeCliMain.VERSION_PROPERTY}=$VERSION",
            FakeSonarQubeCliMain::class.java.name,
        ).joinToString(" ") { "\"$it\"" }
    }

    /** The directory a class was compiled to; the IDE test class loader does not tell it through the code source. */
    private fun classpathRootOf(type: Class<*>): Path {
        val classFile = Path.of(checkNotNull(type.getResource("${type.simpleName}.class")) { "No class file for $type" }.toURI())
        return type.packageName.split('.').fold(classFile.parent) { directory, _ -> directory.parent }
    }

    /** Makes `sonar --version` fail, as a damaged installation would. */
    fun breakInstallation() {
        Files.writeString(binDirectory.resolve(FakeSonarQubeCliMain.BROKEN_FILE), "")
    }

    /** Makes the next `sonar auth login` or `sonar integrate` exit with [exitCode] and change nothing. */
    fun failNextCommand(exitCode: Int) {
        Files.writeString(binDirectory.resolve(FakeSonarQubeCliMain.FAIL_NEXT_COMMAND_FILE), exitCode.toString())
    }

    @OptIn(ExperimentalPathApi::class)
    fun uninstall() {
        installRoot.deleteRecursively()
        commandLog.deleteIfExists()
    }

    companion object {
        /** No real CLI has this version, so the UI showing it proves the backend found this fake. */
        const val VERSION = "0.0.1-fake"
        const val ORGANIZATION = FakeSonarQubeCliMain.DEFAULT_ORGANIZATION
    }
}

private const val SLOOP_USER_HOME_PROPERTY = "sonarlint.test.sloop.user.home"

/** False when the build could not give the backend its own user home, e.g. from a path with spaces. */
fun isSloopUserHomeIsolated(): Boolean = System.getProperty(SLOOP_USER_HOME_PROPERTY) != null

internal fun sloopUserHome(): Path = Path.of(
    checkNotNull(System.getProperty(SLOOP_USER_HOME_PROPERTY)) { "Backend user home is not configured" }
)
