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
package org.sonarlint.intellij.ui.currentfile

import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.fixtures.readOnEdt

class SummaryButtonLightTests : AbstractSonarLintUILightTests() {
    @Test
    fun should_initialize_accessibility_for_updates_before_context_is_requested() {
        readOnEdt {
            val updated = SummaryButton("issue", "issues", {}, "Filter issues")
            updated.update(2, SummaryUiModel())
            assertThat(updated.getAccessibleContext().accessibleName).isEqualTo("2 issues")

            val selected = SummaryButton("issue", "issues", {}, "Filter issues")
            selected.setSelected(true)
            assertThat(selected.getAccessibleContext().accessibleStateSet.contains(AccessibleState.CHECKED)).isTrue()

            val disabled = SummaryButton("issue", "issues", {}, "Filter issues")
            disabled.isEnabled = false
            assertThat(disabled.getAccessibleContext().accessibleStateSet.contains(AccessibleState.ENABLED)).isFalse()
        }
    }

    @Test
    fun should_expose_keyboard_and_accessible_toggle_actions() {
        readOnEdt {
            val filters = mainToolWindow.findings.summaryFilters
            assertThat(filters).hasSize(4)
            filters.forEach { filter ->
                filter.isEnabled = true
                filter.resetSelection()
                val context = filter.accessibleContext
                val checkedEvents = mutableListOf<Any?>()
                context.addPropertyChangeListener { event ->
                    if (event.propertyName == AccessibleContext.ACCESSIBLE_STATE_PROPERTY) checkedEvents.add(event.newValue)
                }
                assertThat(filter.isFocusable).isTrue()
                assertThat(context.accessibleRole).isEqualTo(AccessibleRole.TOGGLE_BUTTON)
                assertThat(context.accessibleName).isNotBlank()
                assertThat(context.accessibleStateSet.contains(AccessibleState.ENABLED)).isTrue()
                filter.pressSpace()
                assertThat(filter.isSelected).isTrue()
                assertThat(context.accessibleStateSet.contains(AccessibleState.CHECKED)).isTrue()
                assertThat(checkedEvents).contains(AccessibleState.CHECKED)
                assertThat(context.accessibleAction.doAccessibleAction(0)).isTrue()
                assertThat(filter.isSelected).isFalse()
                assertThat(context.accessibleAction.doAccessibleAction(1)).isFalse()
                filter.isEnabled = false
                filter.pressSpace()
                assertThat(context.accessibleAction.doAccessibleAction(0)).isFalse()
                assertThat(filter.isSelected).isFalse()
                assertThat(context.accessibleStateSet.contains(AccessibleState.ENABLED)).isFalse()
            }
        }
    }
}
