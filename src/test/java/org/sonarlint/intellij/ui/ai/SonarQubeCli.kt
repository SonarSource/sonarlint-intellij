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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import org.apache.commons.io.file.PathUtils

/**
 * The real SonarQube CLI, installed where its official install script puts it, in the user home the build gives to the
 * backend (see build.gradle.kts). The build points `HOME`, `SONAR_USER_HOME` and `SONARQUBE_CLI_KEYCHAIN_FILE` there too,
 * so the CLI and `sonar integrate` never touch the machine's own setup or keychain. The CLI itself is the latest release,
 * which the build resolves from Artifactory (see `sonarqubeCli` in build.gradle.kts).
 */
class SonarQubeCli {

    private val home = sloopUserHome()
    // On Windows the official location is under %LOCALAPPDATA%, which the build points into the same user home
    private val installRoot = home.resolve(if (SystemInfo.isWindows) "AppData/Local/sonarqube-cli" else ".local/share/sonarqube-cli")
    private val executable = installRoot.resolve(if (SystemInfo.isWindows) "bin/sonar.exe" else "bin/sonar")

    val isInstalled: Boolean
        get() = executable.exists()

    /** The version `sonar --version` prints, e.g. "1.11.0". */
    val version: String
        get() = run(listOf(executable.toString(), "--version")).trim()

    /** Installs the CLI the build resolved from Artifactory where the official install script puts it. */
    fun install() {
        Files.createDirectories(executable.parent)
        Files.copy(releasedCli(), executable, StandardCopyOption.REPLACE_EXISTING)
        check(executable.toFile().setExecutable(true)) { "Cannot make $executable executable" }
    }

    /** Executables running from the CLI installation, by path only: arguments or environment may hold secrets. */
    val runningExecutables: List<String>
        get() = processesRunningFrom(installRoot).mapNotNull { it.info().command().orElse(null) }

    /** Signs in to [serverUrl], as `sonar auth login` does after the browser flow. */
    fun signIn(serverUrl: String, token: String) {
        run(listOf(executable.toString(), "auth", "login", "--with-token", "--server", serverUrl), input = token + "\n")
    }

    /** Damages the installed executable, so that the CLI no longer starts. */
    fun breakInstallation() {
        Files.writeString(executable, "not an executable")
    }

    /** Removes the CLI, its state, its token and what `sonar integrate claude` installed. */
    fun reset() {
        stopRunningCommands()
        deleteDirectoryIfExists(installRoot)
        deleteDirectoryIfExists(home.resolve(".sonar"))
        home.resolve("sonarqube-cli-keychain.json").deleteIfExists()
        deleteDirectoryIfExists(home.resolve(".claude"))
        home.resolve(".claude.json").deleteIfExists()
    }

    private fun releasedCli(): Path =
        Path.of(checkNotNull(System.getProperty(RELEASED_CLI_PROPERTY)) { "The build did not provide the SonarQube CLI, see build.gradle.kts" })

    /**
     * Ends the processes a test left running from the CLI installation, e.g. the background process the CLI may leave:
     * Windows cannot delete an executable while it runs. Do not wait for them to be reaped: in a container without an init
     * process, a killed orphan stays a zombie, which still counts as running but no longer holds its executable.
     */
    private fun stopRunningCommands() {
        val processes = processesRunningFrom(installRoot)
        processes.forEach { it.destroyForcibly() }
        runCatching { CompletableFuture.allOf(*processes.map { it.onExit() }.toTypedArray()).get(5, TimeUnit.SECONDS) }
    }

    /**
     * Deletes [directory], retrying for a few seconds: on Windows a just-ended process, or the antivirus scanning a new
     * executable, can keep files open briefly.
     */
    private fun deleteDirectoryIfExists(directory: Path) {
        var attempt = 0
        while (directory.exists()) {
            try {
                PathUtils.deleteDirectory(directory)
            } catch (e: IOException) {
                if (++attempt == 20) {
                    // Only the executable paths: a process's arguments or environment may hold secrets
                    val runningFromHome = processesRunningFrom(home).mapNotNull { it.info().command().orElse(null) }
                    throw IOException("Cannot delete $directory; processes running from the test user home: $runningFromHome", e)
                }
                Thread.sleep(500)
            }
        }
    }

    private fun processesRunningFrom(directory: Path): List<ProcessHandle> {
        val root = directory.toAbsolutePath().normalize()
        return ProcessHandle.allProcesses().filter { process ->
            process.info().command().map { command ->
                runCatching { Path.of(command.removePrefix("\\\\?\\")).toAbsolutePath().normalize().startsWith(root) }.getOrDefault(false)
            }.orElse(false)
        }.toList()
    }

    private fun run(command: List<String>, input: String? = null): String {
        val process = ProcessBuilder(command).directory(home.toFile()).redirectErrorStream(true).start()
        // Read the output on another thread: reading it here would block until the command ends, past the timeout
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        process.outputStream.use { stdin -> input?.let { stdin.write(it.toByteArray()) } }
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("Timed out: $command")
        }
        val text = output.get(10, TimeUnit.SECONDS)
        check(process.exitValue() == 0) { "Failed with exit code ${process.exitValue()}: $command\n$text" }
        return text
    }

}

private const val RELEASED_CLI_PROPERTY = "sonarlint.test.sonarqube.cli"

/** False when the build could not provide a CLI this machine runs, e.g. without Artifactory credentials or on an Intel Mac. */
fun isSonarQubeCliAvailable(): Boolean = System.getProperty(RELEASED_CLI_PROPERTY) != null

private const val SLOOP_USER_HOME_PROPERTY = "sonarlint.test.sloop.user.home"

/** False when the build could not give the backend its own user home, e.g. from a path with spaces. */
fun isSloopUserHomeIsolated(): Boolean = System.getProperty(SLOOP_USER_HOME_PROPERTY) != null

private fun sloopUserHome(): Path = Path.of(
    checkNotNull(System.getProperty(SLOOP_USER_HOME_PROPERTY)) { "Backend user home is not configured" }
)
