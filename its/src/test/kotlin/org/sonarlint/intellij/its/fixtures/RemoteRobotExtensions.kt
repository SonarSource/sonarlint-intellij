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
package org.sonarlint.intellij.its.fixtures

import com.intellij.remoterobot.RemoteRobot

/** com.intellij.util.PlatformUtils.isCLion() should not be used as it is marked as internal */
fun RemoteRobot.isCLion() = callJs<Boolean>("new String(com.intellij.openapi.application.ApplicationInfo.getInstance().getFullApplicationName()).toLowerCase().includes('clion')")

/** com.intellij.util.PlatformUtils.isGoIde() should not be used as it is marked as internal */
fun RemoteRobot.isGoLand() = callJs<Boolean>("new String(com.intellij.openapi.application.ApplicationInfo.getInstance().getFullApplicationName()).toLowerCase().includes('goland')")

fun RemoteRobot.isPhpStorm() =
    callJs<Boolean>("new String(com.intellij.openapi.application.ApplicationInfo.getInstance().getFullApplicationName()).toLowerCase().includes('phpstorm')")

fun RemoteRobot.isPyCharm() =
    callJs<Boolean>("new String(com.intellij.openapi.application.ApplicationInfo.getInstance().getFullApplicationName()).toLowerCase().includes('pycharm')")

fun RemoteRobot.isRider() =
    callJs<Boolean>("new String(com.intellij.openapi.application.ApplicationInfo.getInstance().getFullApplicationName()).toLowerCase().includes('rider')")

fun RemoteRobot.isIdea() =
    callJs<Boolean>("new String(com.intellij.openapi.application.ApplicationInfo.getInstance().getFullApplicationName()).toLowerCase().includes('idea')")

fun RemoteRobot.isBuildUltimate() =
    callJs<Boolean>("new String(com.intellij.openapi.application.ApplicationInfo.getInstance().getBuild()).toLowerCase().includes('iu')")

fun RemoteRobot.isBuildCommunity() =
    callJs<Boolean>("new String(com.intellij.openapi.application.ApplicationInfo.getInstance().getBuild()).toLowerCase().includes('ic')")

/**
 *  Check if the Go plugin is available, currently bundled in GoLand and as a plugin from the marketplace for IntelliJ
 *  IDEA Ultimate. The plugin was [open source](https://github.com/go-lang-plugin-org/go-lang-idea-plugin/tree/master)
 *  but is now closed source and property of JetBrains. We have to check via the PluginManager to find the plugin by its
 *  id, not its name: org.jetbrains.plugins.go
 */
fun RemoteRobot.isGoPlugin() = callJs<Boolean>("com.intellij.ide.plugins.PluginManager.isPluginInstalled(com.intellij.openapi.extensions.PluginId.getId('org.jetbrains.plugins.go'))")
fun RemoteRobot.isSQLPlugin() = callJs<Boolean>("com.intellij.ide.plugins.PluginManager.isPluginInstalled(com.intellij.openapi.extensions.PluginId.getId('com.intellij.database'))")
fun RemoteRobot.isJavaScriptPlugin() =
    callJs<Boolean>("com.intellij.ide.plugins.PluginManager.isPluginInstalled(com.intellij.openapi.extensions.PluginId.getId('JavaScript'))")

private fun RemoteRobot.majorVersion() =
    callJs<String>("com.intellij.openapi.application.ApplicationInfo.getInstance().getMajorVersion()")

private fun RemoteRobot.minorVersion() =
    callJs<String>("com.intellij.openapi.application.ApplicationInfo.getInstance().getMinorVersion()")

fun RemoteRobot.isModernUI(): Boolean {
    val version = "${this.majorVersion()}.${this.minorVersion()}"
    val referenceVersion = "2024.2"
    val versionParts = version.split(".").map { it.toInt() }
    val referenceParts = referenceVersion.split(".").map { it.toInt() }

    for (i in versionParts.indices) {
        if (i >= referenceParts.size || versionParts[i] > referenceParts[i]) {
            return true
        } else if (versionParts[i] < referenceParts[i]) {
            return false
        }
    }
    return versionParts.size > referenceParts.size
}
