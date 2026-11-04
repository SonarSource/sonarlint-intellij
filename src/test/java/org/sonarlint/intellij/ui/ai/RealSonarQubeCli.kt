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
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/**
 * The real SonarQube CLI, installed by its official script in the user home the build gives to the backend (see
 * build.gradle.kts). The build points `HOME`, `SONAR_USER_HOME` and `SONARQUBE_CLI_KEYCHAIN_FILE` there too, so the CLI,
 * its installer and `sonar integrate` never touch the machine's own setup, shell profile or keychain.
 *
 * Signing in uses the integration tests' SonarQube Cloud staging token, `SONARCLOUD_IT_TOKEN`.
 */
class RealSonarQubeCli {

    private val home = sloopUserHome()
    private val executable = home.resolve(".local/share/sonarqube-cli/bin/sonar")
    // Downloaded once per test run: the user home is wiped at the start of each run
    private val downloadedExecutable = home.resolve(".sonarqube-cli-download/sonar")

    val isInstalled: Boolean
        get() = executable.exists()

    /** The version `sonar --version` prints, e.g. "1.11.0". */
    val version: String
        get() = run(listOf("--version")).trim()

    /** Installs the latest release as the official script does, downloading it only once per test run. */
    fun install() {
        if (!downloadedExecutable.exists()) {
            run(listOf("/bin/bash", "-o", "pipefail", "-c", "curl --fail --silent --show-error --location $INSTALL_SCRIPT_URL | bash"), executable = null)
            Files.createDirectories(downloadedExecutable.parent)
            Files.copy(executable, downloadedExecutable, StandardCopyOption.REPLACE_EXISTING)
            return
        }
        Files.createDirectories(executable.parent)
        Files.copy(downloadedExecutable, executable, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    }

    /** Signs in to SonarQube Cloud staging, as `sonar auth login` would after the browser flow. */
    fun signIn() {
        run(listOf("auth", "login", "--with-token", "--server", STAGING_URL, "--org", ORGANIZATION), input = checkNotNull(token) + "\n")
    }

    /** Removes the CLI, its state, its token and what `sonar integrate claude` installed. */
    @OptIn(ExperimentalPathApi::class)
    fun reset() {
        home.resolve(".local/share/sonarqube-cli").deleteRecursively()
        home.resolve(".sonar").deleteRecursively()
        home.resolve("sonarqube-cli-keychain.json").deleteIfExists()
        home.resolve(".claude").deleteRecursively()
        home.resolve(".claude.json").deleteIfExists()
    }

    private fun run(arguments: List<String>, input: String? = null, executable: Path? = this.executable): String {
        val command = listOfNotNull(executable?.toString()) + arguments
        val process = ProcessBuilder(command).directory(home.toFile()).redirectErrorStream(true).start()
        process.outputStream.use { stdin -> input?.let { stdin.write(it.toByteArray()) } }
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(2, TimeUnit.MINUTES)) { "Timed out: $arguments" }
        check(process.exitValue() == 0) { "Failed with exit code ${process.exitValue()}: $arguments\n$output" }
        return output
    }

    companion object {
        /** The script the backend's install command runs. */
        private const val INSTALL_SCRIPT_URL = "https://raw.githubusercontent.com/SonarSource/sonarqube-cli/refs/heads/master/user-scripts/install.sh"
        const val STAGING_URL = "https://sc-staging.io"
        const val ORGANIZATION = "sonarlint-it"

        private val token: String? = System.getenv("SONARCLOUD_IT_TOKEN")?.takeIf { it.isNotBlank() }

        /** False without the staging token, e.g. on a developer machine. */
        val canSignIn: Boolean = token != null
    }
}
