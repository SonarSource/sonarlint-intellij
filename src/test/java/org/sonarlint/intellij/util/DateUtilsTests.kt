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
package org.sonarlint.intellij.util

import java.time.LocalDateTime
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DateUtilsTests {

    @Test
    fun should_display_date_in_years() {
        val now = LocalDateTime.now()

        val oneYearAgo = now.minusYears(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(DateUtils.toAge(oneYearAgo)).isEqualTo("1 year ago")
    }

    @Test
    fun should_display_date_in_months() {
        val now = LocalDateTime.now()

        val oneMonthAgo = now.minusMonths(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(DateUtils.toAge(oneMonthAgo)).isEqualTo("1 month ago")
    }

    @Test
    fun should_display_date_in_months_pluralized() {
        val now = LocalDateTime.now()

        val twoMonthsAgo = now.minusMonths(2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(DateUtils.toAge(twoMonthsAgo)).isEqualTo("2 months ago")
    }

    @Test
    fun should_display_date_in_days() {
        val now = LocalDateTime.now()

        val tenDaysAgo = now.minusDays(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(DateUtils.toAge(tenDaysAgo)).isEqualTo("10 days ago")
    }

    @Test
    fun should_display_date_in_hours() {
        val now = LocalDateTime.now()

        val fiveHoursAgo = now.minusHours(5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(DateUtils.toAge(fiveHoursAgo)).isEqualTo("5 hours ago")
    }

    @Test
    fun should_display_date_in_miuntes() {
        val now = LocalDateTime.now()

        val thirtyMinutesAgo = now.minusMinutes(30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(DateUtils.toAge(thirtyMinutesAgo)).isEqualTo("30 minutes ago")
    }

    @Test
    fun should_display_date_in_seconds() {
        val now = LocalDateTime.now()

        val fewSecondsAgo = now.minusSeconds(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertThat(DateUtils.toAge(fewSecondsAgo)).isEqualTo("few seconds ago")
    }

}
