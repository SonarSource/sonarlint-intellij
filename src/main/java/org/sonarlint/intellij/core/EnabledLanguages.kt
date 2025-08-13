/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.core

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import kotlin.io.path.name
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

private const val JUPYTER_PLUGIN_ID = "intellij.jupyter"
private const val DATABASE_PLUGIN_ID = "com.intellij.database"
private const val JAVA_MODULE_ID = "com.intellij.modules.java"
private const val GO_PLUGIN_ID = "org.jetbrains.plugins.go"
private const val CLION_MODULE_ID = "com.intellij.modules.clion"
private const val RIDER_MODULE_ID = "com.intellij.modules.rider"

object EnabledLanguages {
    private val ENABLED_LANGUAGES_IN_STANDALONE_MODE_IN_IDEA: Set<Language> = EnumSet.of(
        Language.HTML,
        Language.XML,
        Language.JS,
        Language.CSS,
        Language.KOTLIN,
        Language.PHP,
        Language.PYTHON,
        Language.RUBY,
        Language.SECRETS,
        Language.TS,
        Language.YAML,
        Language.CLOUDFORMATION,
        Language.DOCKER,
        Language.KUBERNETES,
        Language.TERRAFORM
    )
    private val EMBEDDED_PLUGINS_TO_USE_IN_CONNECTED_MODE = listOf(
        EmbeddedPlugin(Language.CPP, "CFamily", "sonar-cfamily-plugin-*.jar"),
        EmbeddedPlugin(Language.HTML, "HTML", "sonar-html-plugin-*.jar"),
        EmbeddedPlugin(Language.JS, "JavaScript/TypeScript", "sonar-javascript-plugin-*.jar"),
        EmbeddedPlugin(Language.KOTLIN, "Kotlin", "sonar-kotlin-plugin-*.jar"),
        EmbeddedPlugin(Language.RUBY, "Ruby", "sonar-ruby-plugin-*.jar"),
        EmbeddedPlugin(Language.XML, "XML", "sonar-xml-plugin-*.jar"),
        EmbeddedPlugin(Language.SECRETS, "Secrets detection", "sonar-text-plugin-*.jar"),
        EmbeddedPlugin(Language.GO, "Go", "sonar-go-plugin-*.jar"),
        EmbeddedPlugin(org.sonarsource.sonarlint.core.commons.api.SonarLanguage.valueOf(Language.CLOUDFORMATION.name).pluginKey, "IaC", "sonar-iac-plugin-*.jar")
    )

    @JvmStatic
    fun getEmbeddedPluginsForConnectedMode(): Map<String, Path> {
        val embeddedPlugins = mutableMapOf<String, Path>()
        EMBEDDED_PLUGINS_TO_USE_IN_CONNECTED_MODE.forEach {
            findEmbeddedPlugin(getPluginsDir(), it)?.let { path ->
                embeddedPlugins.put(it.pluginKey, path)
            }
        }
        findEmbeddedPlugin(getPluginsDir(), "sonarlint-omnisharp-plugin-*.jar", "OmniSharp")?.let {
            embeddedPlugins.put("omnisharp", it)
        }
        return embeddedPlugins
    }

    @JvmStatic
    val enabledLanguagesInStandaloneMode: Set<Language>
        get() {
            return when {
                isIdeModuleEnabled(CLION_MODULE_ID) -> {
                    EnumSet.of(Language.C, Language.CPP, Language.SECRETS)
                }

                isIdeModuleEnabled(RIDER_MODULE_ID) -> {
                    EnumSet.of(Language.CS, Language.SECRETS, Language.HTML, Language.CSS, Language.JS, Language.TS)
                }

                else -> {
                    // all other IDEs
                    val enabledLanguages = EnumSet.copyOf(ENABLED_LANGUAGES_IN_STANDALONE_MODE_IN_IDEA)
                    if (isIdeModuleEnabled(JAVA_MODULE_ID)) {
                        enabledLanguages.add(Language.JAVA)
                    }
                    if (isIdeModuleEnabled(GO_PLUGIN_ID)) {
                        enabledLanguages.add(Language.GO)
                    }
                    if (isIdeModuleEnabled(JUPYTER_PLUGIN_ID)) {
                        enabledLanguages.add(Language.IPYTHON)
                    }
                    enabledLanguages
                }
            }
        }

    @JvmStatic
    val extraEnabledLanguagesInConnectedMode: Set<Language>
        get() {
            val extraEnabledLanguages = EnumSet.of(Language.ANSIBLE, Language.TEXT)

            return when {
                isIdeModuleEnabled(RIDER_MODULE_ID) -> {
                    extraEnabledLanguages
                }

                else -> {
                    if (isIdeModuleEnabled(DATABASE_PLUGIN_ID)) {
                        extraEnabledLanguages.add(Language.PLSQL)
                    }
                    if (!isIdeModuleEnabled(CLION_MODULE_ID)) {
                        // all other IDEs
                        extraEnabledLanguages.addAll(EnumSet.of(Language.SCALA, Language.SWIFT))
                    }

                    extraEnabledLanguages
                }
            }
        }

    @JvmStatic
    @Throws(IOException::class)
    fun findEmbeddedPlugins(): Set<Path> {
        return getPluginsUrls(getPluginsDir())
    }

    private fun getPluginsDir(): Path {
        val plugin = SonarLintUtils.getService(SonarLintPlugin::class.java)
        return plugin.path.resolve("plugins")
    }

    private fun findEmbeddedPlugin(pluginsDir: Path, embeddedPlugin: EmbeddedPlugin): Path? {
        return findEmbeddedPlugin(pluginsDir, embeddedPlugin.jarFilePattern, embeddedPlugin.name)
    }

    private fun findEmbeddedPlugin(pluginsDir: Path, jarFilePattern: String, pluginName: String): Path? {
        return try {
            val pluginsPaths = findFilesInDir(pluginsDir, jarFilePattern, "Found " + pluginName + " plugin: ")
            check(pluginsPaths.size <= 1) { "Multiple plugins found" }
            if (pluginsPaths.size == 1) pluginsPaths.first() else null
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    @Throws(IOException::class)
    private fun getPluginsUrls(pluginsDir: Path): Set<Path> {
        return findFilesInDir(pluginsDir, "*.jar", "Found plugin: ")
    }

    @Throws(IOException::class)
    private fun findFilesInDir(pluginsDir: Path, pattern: String, logPrefix: String): Set<Path> {
        val pluginsPaths = HashSet<Path>()
        if (Files.isDirectory(pluginsDir)) {
            Files.newDirectoryStream(pluginsDir, pattern).use { directoryStream ->
                val globalLogOutput = SonarLintUtils.getService(GlobalLogOutput::class.java)
                for (path in directoryStream) {
                    // Do not load C# analyzers, they are handled differently
                    if (!path.name.contains("csharp")) {
                        globalLogOutput.log(logPrefix + path.fileName.toString(), ClientLogOutput.Level.DEBUG)
                        pluginsPaths.add(path)
                    }
                }
            }
        }
        return pluginsPaths
    }

    private class EmbeddedPlugin(val pluginKey: String, val name: String, val jarFilePattern: String) {
        constructor(language: Language, name: String, jarFilePattern: String) : this(org.sonarsource.sonarlint.core.commons.api.SonarLanguage.valueOf(language.name).pluginKey, name, jarFilePattern)
    }

    private fun isIdeModuleEnabled(pluginId: String) = PluginManagerCore.getPlugin(PluginId.getId(pluginId))?.isEnabled == true
}
