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
package org.sonarlint.intellij.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JcefAvailabilityTests {

    @Test
    fun should_return_true_when_jcef_is_supported() {
        assertThat(JcefAvailability.isSupported { true }).isTrue()
    }

    @Test
    fun should_return_false_when_jcef_is_not_supported() {
        assertThat(JcefAvailability.isSupported { false }).isFalse()
    }

    @Test
    fun should_return_false_when_jcef_class_is_missing() {
        assertThat(
            JcefAvailability.isSupported { throw NoClassDefFoundError("com/intellij/ui/jcef/JBCefApp") }
        ).isFalse()
    }

}
