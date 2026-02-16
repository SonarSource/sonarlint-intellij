/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.integration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

private const val DEVOXX_GENIE_PLUGIN_ID = "com.devoxx.genie"
private const val EXTERNAL_PROMPT_SERVICE_CLASS = "com.devoxx.genie.service.ExternalPromptService"

object DevoxxGenieBridge {

    private val LOG = Logger.getInstance(DevoxxGenieBridge::class.java)

    private fun getPluginClassLoader(): ClassLoader? {
        val pluginId = PluginId.getId(DEVOXX_GENIE_PLUGIN_ID)
        val descriptor = PluginManagerCore.getPlugin(pluginId) ?: return null
        return if (descriptor.isEnabled) descriptor.pluginClassLoader else null
    }

    fun isAvailable(): Boolean = getPluginClassLoader() != null

    fun sendPrompt(project: Project, text: String): Boolean {
        return try {
            val classLoader = getPluginClassLoader() ?: return false
            val serviceClass = Class.forName(EXTERNAL_PROMPT_SERVICE_CLASS, true, classLoader)
            val getInstance = serviceClass.getMethod("getInstance", Project::class.java)
            val service = getInstance.invoke(null, project)
            if (service == null) {
                LOG.warn("DevoxxGenie ExternalPromptService.getInstance() returned null")
                return false
            }
            val setPromptText = serviceClass.getMethod("setPromptText", String::class.java)
            val result = setPromptText.invoke(service, text)
            val success = result as? Boolean ?: false
            if (!success) {
                LOG.warn("DevoxxGenie setPromptText returned false — tool window may not have been opened yet")
            }
            success
        } catch (e: Exception) {
            LOG.warn("Failed to send prompt to DevoxxGenie", e)
            false
        }
    }
}
