/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

fun RemoteRobot.ideMajorVersion() = callJs<Int>("com.intellij.openapi.application.ApplicationInfo.getInstance().getBuild().getBaselineVersion()")

fun RemoteRobot.isCLion() = callJs<Boolean>("com.intellij.util.PlatformUtils.isCLion()")

fun RemoteRobot.isGoLand() = callJs<Boolean>("com.intellij.util.PlatformUtils.isGoIde()")

/**
 *  Check if the Go plugin is available, currently bundled in GoLand and as a plugin from the marketplace for IntelliJ
 *  IDEA Ultimate. The plugin was [open source](https://github.com/go-lang-plugin-org/go-lang-idea-plugin/tree/master)
 *  but is now closed source and property of JetBrains. As the SDK provides no API for getting a list of all installed
 *  plugin ids (Go plugin id: org.jetbrains.plugins.go), we have to test for the actual
 *  [implementation](https://github.com/go-lang-plugin-org/go-lang-idea-plugin/blob/master/src/com/goide/GoLanguage.java),
 *  which hasn't changed for over 8+ years.
 */
fun RemoteRobot.isGoPlugin() = callJs<Boolean>(
    "try { java.lang.Class.forName('com.goide.GoLanguage'); return true; } catch (ignored) { return false; }"
)
