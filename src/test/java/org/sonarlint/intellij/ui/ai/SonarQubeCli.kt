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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import org.apache.commons.io.file.PathUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService

/**
 * The real SonarQube CLI, installed where its official script puts it, in the user home the build gives to the backend
 * (see build.gradle.kts). The build points `HOME`, `SONAR_USER_HOME` and `SONARQUBE_CLI_KEYCHAIN_FILE` there too, so the
 * CLI, its installer and `sonar integrate` never touch the machine's own setup, shell profile or keychain.
 */
class SonarQubeCli {

    private val home = sloopUserHome()
    private val installRoot = home.resolve(".local/share/sonarqube-cli")
    private val executable = installRoot.resolve("bin/sonar")
    // Downloaded once per test run, outside the install location: the user home is wiped at the start of each run
    private val downloadedExecutable = home.resolve(".sonarqube-cli-download/sonar")

    val isInstalled: Boolean
        get() = executable.exists()

    /** The version `sonar --version` prints, e.g. "1.11.0". */
    val version: String
        get() = run(listOf(executable.toString(), "--version")).trim()

    /** Installs the latest release. */
    fun install() {
        val downloaded = download()
        Files.createDirectories(executable.parent)
        Files.copy(downloaded, executable, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    }

    /** Signs in to SonarQube Cloud staging, as `sonar auth login` does after the browser flow. */
    fun signIn() {
        run(
            listOf(executable.toString(), "auth", "login", "--with-token", "--server", SonarQubeCloudStaging.URL, "--org", SonarQubeCloudStaging.ORGANIZATION),
            input = checkNotNull(SonarQubeCloudStaging.token) + "\n",
        )
    }

    /** Damages the installed executable, so that the CLI no longer starts. */
    fun breakInstallation() {
        Files.writeString(executable, "not an executable")
    }

    /**
     * Removes the CLI, its state, its token and what `sonar integrate claude` installed. A CLI installed from the card is
     * kept for the next tests instead, so a test run downloads the CLI only once.
     */
    fun reset() {
        if (isInstalled && !downloadedExecutable.exists()) {
            Files.createDirectories(downloadedExecutable.parent)
            Files.move(executable, downloadedExecutable)
        }
        deleteDirectoryIfExists(installRoot)
        deleteDirectoryIfExists(home.resolve(".sonar"))
        home.resolve("sonarqube-cli-keychain.json").deleteIfExists()
        deleteDirectoryIfExists(home.resolve(".claude"))
        home.resolve(".claude.json").deleteIfExists()
    }

    /**
     * The latest release, kept from the card's install test, or else installed with the backend's install command, the one
     * the card runs in the terminal.
     */
    private fun download(): Path {
        if (!downloadedExecutable.exists()) {
            val installCommand = getService(BackendService::class.java).prepareInstallCliCommand().get(30, TimeUnit.SECONDS)
            run(listOf(installCommand.executable) + installCommand.arguments)
            Files.createDirectories(downloadedExecutable.parent)
            Files.move(executable, downloadedExecutable, StandardCopyOption.REPLACE_EXISTING)
        }
        return downloadedExecutable
    }

    private fun deleteDirectoryIfExists(directory: Path) {
        if (directory.exists()) {
            PathUtils.deleteDirectory(directory)
        }
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

private const val SLOOP_USER_HOME_PROPERTY = "sonarlint.test.sloop.user.home"

/** False when the build could not give the backend its own user home, e.g. from a path with spaces. */
fun isSloopUserHomeIsolated(): Boolean = System.getProperty(SLOOP_USER_HOME_PROPERTY) != null

private fun sloopUserHome(): Path = Path.of(
    checkNotNull(System.getProperty(SLOOP_USER_HOME_PROPERTY)) { "Backend user home is not configured" }
)
