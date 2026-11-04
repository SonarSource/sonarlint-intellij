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
 * Stands in for a SonarQube CLI installed by the official script, in the user home the build gives to the backend (see
 * `sonarlint.test.sloop.user.home` in build.gradle.kts), and passes every command on to the real CLI: a one-line launcher
 * runs [FakeSonarQubeCliMain]. Use it only for what the real CLI cannot do on demand: a damaged installation, a failing
 * sign-in, and signing in from the card without a browser.
 */
class FakeSonarQubeCli(private val realCli: RealSonarQubeCli) {

    private val installRoot = sloopUserHome().resolve(".local/share/sonarqube-cli")
    private val binDirectory = installRoot.resolve("bin")
    private val commandLog = sloopUserHome().resolve("fake-sonar-commands.log")

    /** Arguments of every command the backend or the terminal ran, oldest first. */
    val receivedCommands: List<String>
        get() = if (commandLog.exists()) commandLog.readLines() else emptyList()

    fun install() {
        val launchCommand = launchCommand(realCli.download())
        Files.createDirectories(binDirectory)
        val executable = binDirectory.resolve("sonar")
        Files.writeString(executable, "#!/bin/sh\nexec $launchCommand \"${'$'}@\"\n")
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    /** Runs [FakeSonarQubeCliMain] on the test JVM's runtime, with the compiled test classes as its only classpath entry. */
    private fun launchCommand(realExecutable: Path): String {
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        val testClasses = classpathRootOf(FakeSonarQubeCliMain::class.java)
        return listOf(
            java.toString(), "-cp", testClasses.toString(),
            "-D${FakeSonarQubeCliMain.REAL_EXECUTABLE_PROPERTY}=$realExecutable",
            "-D${FakeSonarQubeCliMain.STATE_DIRECTORY_PROPERTY}=$binDirectory",
            "-D${FakeSonarQubeCliMain.COMMAND_LOG_PROPERTY}=$commandLog",
            "-D${FakeSonarQubeCliMain.SERVER_PROPERTY}=${RealSonarQubeCli.STAGING_URL}",
            "-D${FakeSonarQubeCliMain.ORGANIZATION_PROPERTY}=${RealSonarQubeCli.ORGANIZATION}",
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

    /** Makes the next `sonar auth login` exit with [exitCode] and change nothing. */
    fun failNextSignIn(exitCode: Int) {
        Files.writeString(binDirectory.resolve(FakeSonarQubeCliMain.FAIL_NEXT_SIGN_IN_FILE), exitCode.toString())
    }

    @OptIn(ExperimentalPathApi::class)
    fun uninstall() {
        installRoot.deleteRecursively()
        commandLog.deleteIfExists()
    }
}

private const val SLOOP_USER_HOME_PROPERTY = "sonarlint.test.sloop.user.home"

/** False when the build could not give the backend its own user home, e.g. from a path with spaces. */
fun isSloopUserHomeIsolated(): Boolean = System.getProperty(SLOOP_USER_HOME_PROPERTY) != null

internal fun sloopUserHome(): Path = Path.of(
    checkNotNull(System.getProperty(SLOOP_USER_HOME_PROPERTY)) { "Backend user home is not configured" }
)
