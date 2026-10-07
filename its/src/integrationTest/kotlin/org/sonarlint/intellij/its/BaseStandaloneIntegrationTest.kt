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
package org.sonarlint.intellij.its

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.Starter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.TestInfo
import org.sonarlint.intellij.its.driver.waitForBackgroundTasksFinished
import org.sonarlint.intellij.its.utils.ProjectCopier

open class BaseStandaloneIntegrationTest {

    protected fun uiTest(testInfo: TestInfo, projectName: String, testBody: Driver.() -> Unit) {
        createContext(testInfo, projectName).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            waitForBackgroundTasksFinished()
            testBody()
        }
    }

    protected fun uiTestWithoutProject(testInfo: TestInfo, testBody: Driver.() -> Unit) {
        createContext(testInfo, projectName = null).runIdeWithDriver().useDriverAndCloseIde {
            testBody()
        }
    }

    protected fun createContext(testInfo: TestInfo, projectName: String?): IDETestContext {
        val projectInfo = if (projectName == null) {
            NoProject
        } else {
            val projectPath = ProjectCopier.prepareProject(projectName)
            LocalProjectInfo(projectPath)
        }

        return Starter.newContext(
            testInfo.displayName,
            TestCase(resolveIdeInfoWithLocalInstaller(), projectInfo)
        )
            .apply { PluginConfigurator(this).installPluginFromPath(resolvePluginArchive()) }
            .prepareProjectCleanImport()
            .apply {
                if (projectName != null) {
                    addProjectToTrustedLocations(ProjectCopier.prepareProject(projectName), true)
                }
            }
            .allowSkippingFullScanning(true)
            .setSharedIndexesDownload(true)
            .skipGitLogIndexing(true)
            .disablePackageSearchBuildFiles()
            .disableAIAssistantToolwindowActivationOnStart()
            .setMemorySize(4096)
            .applyVMOptionsPatch {
                addSystemProperty("sonarlint.internal.sonarcloud.url", "https://sc-staging.io")
                addSystemProperty("sonarlint.internal.sonarcloud.api.url", "https://api.sc-staging.io")
                addSystemProperty("sonarlint.internal.sonarcloud.websocket.url", "wss://events-api.sc-staging.io/")
                addSystemProperty("sonarlint.internal.sonarcloud.us.url", "https://us-sc-staging.io")
                addSystemProperty("sonarlint.internal.sonarcloud.us.api.url", "https://api.us-sc-staging.io")
                addSystemProperty("sonarlint.internal.sonarcloud.us.websocket.url", "wss://events-api.us-sc-staging.io")
                addSystemProperty("sonarlint.telemetry.disabled", "true")
                addSystemProperty("sonarlint.monitoring.disabled", "true")
                addSystemProperty("sonarlint.logs.verbose", "true")
                addSystemProperty("idea.trust.all.projects", "true")
                addSystemProperty("ide.show.tips.on.startup.default.value", "false")
                addSystemProperty("ide.experimental.ui.navbar.scroll", "true")
                addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
                addSystemProperty("jb.consents.confirmation.enabled", "false")
                addSystemProperty("eap.require.license", "true")
                addSystemProperty("ide.mac.message.dialogs.as.sheets", "false")
                addSystemProperty("ide.mac.file.chooser.native", "false")
            }
            .also { skipAutoTrial(it) }
    }

    companion object {
        const val PLUGIN_ID = "org.sonarlint.idea"

        @JvmStatic
        fun isIdeaCommunity(): Boolean = ideProduct() == "IC"

        @JvmStatic
        fun isIdeaUltimate(): Boolean = ideProduct() == "IU"

        @JvmStatic
        fun isCLion(): Boolean = ideProduct() == "CL"

        @JvmStatic
        fun isRider(): Boolean = ideProduct() == "RD"

        @JvmStatic
        fun isPhpStorm(): Boolean = ideProduct() == "PS"

        @JvmStatic
        fun isPyCharm(): Boolean = ideProduct() == "PY" || ideProduct() == "PC"

        @JvmStatic
        fun isGoPlugin(): Boolean = ideProduct() == "GO"

        @JvmStatic
        fun isWebStorm(): Boolean = ideProduct() == "IU"

        private fun ideProduct(): String = System.getProperty("its.ide.product", "IC")

        private fun resolveIdeInfoForProductCode(productCode: String) = when (productCode) {
            "IC" -> IdeProductProvider.IC
            "IU" -> IdeProductProvider.IU
            "CL" -> IdeProductProvider.CL
            // IdeProductProvider has RR (RustRover) but no RD; define Rider metadata explicitly.
            "RD" -> IdeProductProvider.CL.copy(
                productCode = "RD",
                platformPrefix = "Rider",
                executableFileName = "rider",
                fullName = "Rider",
            )
            "PS" -> IdeProductProvider.PS
            "PY" -> IdeProductProvider.PY
            "PC" -> IdeProductProvider.PC
            "GO" -> IdeProductProvider.GO
            else -> throw IllegalArgumentException("Unsupported IDE product for standalone ITs: $productCode")
        }

        private fun readInstalledIdeInfo(ideHome: Path): InstalledIdeInfo {
            val productInfo = ideHome.resolve("product-info.json")
            check(Files.isRegularFile(productInfo)) { "Missing product-info.json in $ideHome" }
            val json = Files.readString(productInfo)
            fun field(name: String): String =
                Regex(""""$name"\s*:\s*"([^"]+)"""")
                    .find(json)?.groupValues?.get(1)
                    ?: error("Missing '$name' in $productInfo")
            return InstalledIdeInfo(
                productCode = field("productCode"),
                buildNumber = field("buildNumber"),
                version = field("version"),
            )
        }

        private fun resolveIdeInfoWithLocalInstaller(): IdeInfo {
            val ideHome = resolveLocalIdeHome()
            val installed = readInstalledIdeInfo(ideHome)
            // 2025.3+ unified distributions report IU/PY even when CI matrix uses IC/PC.
            return resolveIdeInfoForProductCode(installed.productCode).copy(
                buildNumber = installed.buildNumber,
                version = installed.version,
                getInstaller = { ExistingIdeInstaller(ideHome) },
            )
        }

        private data class InstalledIdeInfo(
            val productCode: String,
            val buildNumber: String,
            val version: String,
        )

        private fun resolveLocalIdeHome(): Path {
            System.getProperty("its.ide.home")?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }

            val envVar = when (ideProduct()) {
                "IC", "IU" -> "IDEA_HOME"
                "CL" -> "CLION_HOME"
                "RD" -> "RIDER_HOME"
                "PS" -> "PHPSTORM_HOME"
                "PY", "PC" -> "PYCHARM_HOME"
                "GO" -> "GOLAND_HOME"
                else -> throw IllegalArgumentException("Unsupported IDE product for standalone ITs: ${ideProduct()}")
            }
            System.getenv(envVar)?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }

            throw IllegalStateException(
                "No local IDE path for standalone ITs. Set $envVar or run ./gradlew :its:integrationTest"
            )
        }

        private fun requiredProperty(name: String): String =
            checkNotNull(System.getProperty(name)?.takeIf { it.isNotBlank() }) {
                "System property '$name' is not set. Run ./gradlew :its:integrationTest"
            }

        private fun resolvePluginArchive(): Path {
            System.getProperty("its.plugin.archive")?.takeIf { it.isNotBlank() }?.let { archive ->
                val path = Path.of(archive)
                if (Files.isRegularFile(path)) {
                    return path
                }
            }

            val pluginPath = Path.of(requiredProperty("its.plugin.distributions.dir"))
            if (Files.isRegularFile(pluginPath) && isPluginDistributionZip(pluginPath)) {
                return pluginPath
            }
            return Files.list(pluginPath).use { stream ->
                stream.filter { path -> isPluginDistributionZip(path) }
                    .max(java.util.Comparator.comparingLong { Files.getLastModifiedTime(it).toMillis() })
                    .orElseThrow { IllegalStateException("No plugin zip found in $pluginPath") }
            }
        }

        private fun isPluginDistributionZip(path: Path): Boolean {
            val name = path.fileName.toString()
            return name.startsWith("sonarlint-intellij-") && name.endsWith(".zip") && !name.contains(".blockmap")
        }

        private fun skipAutoTrial(context: IDETestContext) {
            val configDir = context.paths.configDir
            configDir.createDirectories()
            configDir.resolve(".ce_migration_attempted").createFile()
            copyTestIdeConfig(configDir)
        }

        private fun copyTestIdeConfig(configDir: Path) {
            val ideaConfig = Path.of(System.getProperty("its.idea.config.dir", ""))
            if (ideaConfig.toString().isBlank() || !Files.isDirectory(ideaConfig)) {
                return
            }
            Files.walk(ideaConfig).forEach { source ->
                val target = configDir.resolve(ideaConfig.relativize(source).toString())
                if (Files.isDirectory(source)) {
                    target.createDirectories()
                } else {
                    target.parent?.createDirectories()
                    Files.copy(source, target)
                }
            }
        }
    }
}
