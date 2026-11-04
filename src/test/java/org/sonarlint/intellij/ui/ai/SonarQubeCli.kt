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
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readLines
import org.apache.commons.io.file.PathUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService

/**
 * The real SonarQube CLI, installed where its official script puts it, in the user home the build gives to the backend
 * (see build.gradle.kts). The build points `HOME`, `SONAR_USER_HOME` and `SONARQUBE_CLI_KEYCHAIN_FILE` there too, so the
 * CLI, its installer and `sonar integrate` never touch the machine's own setup, shell profile or keychain.
 *
 * [install] puts a thin wrapper there, which passes every command on to the real CLI (see [SonarQubeCliWrapperMain]).
 * It records the commands, can break the installation or fail the next sign-in on demand, and signs in to SonarQube Cloud
 * staging with the integration tests' token, `SONARCLOUD_IT_TOKEN`, where the real `sonar auth login` would open a
 * browser.
 */
class SonarQubeCli {

    private val home = sloopUserHome()
    private val installRoot = home.resolve(".local/share/sonarqube-cli")
    private val binDirectory = installRoot.resolve("bin")
    private val executable = binDirectory.resolve("sonar")
    // Downloaded once per test run, outside the install location: the user home is wiped at the start of each run
    private val downloadedExecutable = home.resolve(".sonarqube-cli-download/sonar")
    private val commandLog = home.resolve("sonarqube-cli-commands.log")

    val isInstalled: Boolean
        get() = executable.exists()

    /** The version `sonar --version` prints, e.g. "1.11.0". */
    val version: String
        get() = run(listOf("--version")).trim()

    /** Arguments of every command the backend or the terminal ran through the wrapper, oldest first. */
    val receivedCommands: List<String>
        get() = if (commandLog.exists()) commandLog.readLines() else emptyList()

    /** Installs the latest release, behind the wrapper. */
    fun install() {
        val realExecutable = download()
        Files.createDirectories(binDirectory)
        Files.writeString(executable, "#!/bin/sh\nexec ${wrapperCommand(realExecutable)} \"${'$'}@\"\n")
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    /** Signs in to SonarQube Cloud staging, as `sonar auth login` would after the browser flow. */
    fun signIn() {
        run(listOf("auth", "login", "--with-token", "--server", STAGING_URL, "--org", ORGANIZATION), input = checkNotNull(token) + "\n")
    }

    /** Makes `sonar --version` fail, as a damaged installation would. */
    fun breakInstallation() {
        Files.writeString(binDirectory.resolve(SonarQubeCliWrapperMain.BROKEN_FILE), "")
    }

    /** Makes the next `sonar auth login` exit with [exitCode] and change nothing. */
    fun failNextSignIn(exitCode: Int) {
        Files.writeString(binDirectory.resolve(SonarQubeCliWrapperMain.FAIL_NEXT_SIGN_IN_FILE), exitCode.toString())
    }

    /** Removes the CLI, its state, its token and what `sonar integrate claude` installed. */
    fun reset() {
        deleteDirectoryIfExists(installRoot)
        commandLog.deleteIfExists()
        deleteDirectoryIfExists(home.resolve(".sonar"))
        home.resolve("sonarqube-cli-keychain.json").deleteIfExists()
        deleteDirectoryIfExists(home.resolve(".claude"))
        home.resolve(".claude.json").deleteIfExists()
    }

    private fun deleteDirectoryIfExists(directory: Path) {
        if (directory.exists()) {
            PathUtils.deleteDirectory(directory)
        }
    }

    /**
     * The latest release, installed once per test run with the backend's install command, the one the card runs in the
     * terminal, then moved out of the install location to leave room for the wrapper.
     */
    private fun download(): Path {
        if (!downloadedExecutable.exists()) {
            val installCommand = getService(BackendService::class.java).prepareInstallCliCommand().get(30, TimeUnit.SECONDS)
            run(listOf(installCommand.executable) + installCommand.arguments, executable = null)
            Files.createDirectories(downloadedExecutable.parent)
            Files.move(executable, downloadedExecutable, StandardCopyOption.REPLACE_EXISTING)
        }
        return downloadedExecutable
    }

    /** Runs [SonarQubeCliWrapperMain] on the test JVM's runtime, with the compiled test classes as its only classpath entry. */
    private fun wrapperCommand(realExecutable: Path): String {
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        val testClasses = classpathRootOf(SonarQubeCliWrapperMain::class.java)
        return listOf(
            java.toString(), "-cp", testClasses.toString(),
            "-D${SonarQubeCliWrapperMain.REAL_EXECUTABLE_PROPERTY}=$realExecutable",
            "-D${SonarQubeCliWrapperMain.STATE_DIRECTORY_PROPERTY}=$binDirectory",
            "-D${SonarQubeCliWrapperMain.COMMAND_LOG_PROPERTY}=$commandLog",
            "-D${SonarQubeCliWrapperMain.SERVER_PROPERTY}=$STAGING_URL",
            "-D${SonarQubeCliWrapperMain.ORGANIZATION_PROPERTY}=$ORGANIZATION",
            SonarQubeCliWrapperMain::class.java.name,
        ).joinToString(" ") { "\"$it\"" }
    }

    /** The directory a class was compiled to; the IDE test class loader does not tell it through the code source. */
    private fun classpathRootOf(type: Class<*>): Path {
        val classFile = Path.of(checkNotNull(type.getResource("${type.simpleName}.class")) { "No class file for $type" }.toURI())
        return type.packageName.split('.').fold(classFile.parent) { directory, _ -> directory.parent }
    }

    private fun run(arguments: List<String>, input: String? = null, executable: Path? = this.executable): String {
        val command = listOfNotNull(executable?.toString()) + arguments
        val process = ProcessBuilder(command).directory(home.toFile()).redirectErrorStream(true).start()
        // Read the output on another thread: reading it here would block until the command ends, past the timeout
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        process.outputStream.use { stdin -> input?.let { stdin.write(it.toByteArray()) } }
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("Timed out: $arguments")
        }
        val text = output.get(10, TimeUnit.SECONDS)
        check(process.exitValue() == 0) { "Failed with exit code ${process.exitValue()}: $arguments\n$text" }
        return text
    }

    companion object {
        const val STAGING_URL = "https://sc-staging.io"
        const val ORGANIZATION = "sonarlint-it"

        private val token: String? = System.getenv(SonarQubeCliWrapperMain.TOKEN_VARIABLE)?.takeIf { it.isNotBlank() }

        /** False without the staging token, e.g. on a developer machine. */
        val canSignIn: Boolean = token != null
    }
}

private const val SLOOP_USER_HOME_PROPERTY = "sonarlint.test.sloop.user.home"

/** False when the build could not give the backend its own user home, e.g. from a path with spaces. */
fun isSloopUserHomeIsolated(): Boolean = System.getProperty(SLOOP_USER_HOME_PROPERTY) != null

private fun sloopUserHome(): Path = Path.of(
    checkNotNull(System.getProperty(SLOOP_USER_HOME_PROPERTY)) { "Backend user home is not configured" }
)
