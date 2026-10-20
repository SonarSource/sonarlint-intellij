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
package org.sonarlint.intellij.fixtures

import java.awt.Component
import java.awt.Container
import javax.swing.JComponent

fun Component.walkComponents(): Sequence<Component> = sequence {
    yield(this@walkComponents)
    val container = this@walkComponents as? Container ?: return@sequence
    for (child in container.components) {
        yieldAll(child.walkComponents())
    }
}

inline fun <reified T : Component> Component.findComponents(): List<T> =
    walkComponents().filterIsInstance<T>().toList()

inline fun <reified T : Component> JComponent.findComponents(): List<T> =
    (this as Component).findComponents()
