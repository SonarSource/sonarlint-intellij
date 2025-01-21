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

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object DateUtils {
    @JvmStatic
    fun toAge(time: Long): String {
        val creation = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
        val now = LocalDateTime.now()

        val years = ChronoUnit.YEARS.between(creation, now)
        if (years > 0) {
            return pluralize(years, "year")
        }
        val months = ChronoUnit.MONTHS.between(creation, now)
        if (months > 0) {
            return pluralize(months, "month")
        }
        val days = ChronoUnit.DAYS.between(creation, now)
        if (days > 0) {
            return pluralize(days, "day")
        }
        val hours = ChronoUnit.HOURS.between(creation, now)
        if (hours > 0) {
            return pluralize(hours, "hour")
        }
        val minutes = ChronoUnit.MINUTES.between(creation, now)
        if (minutes > 0) {
            return pluralize(minutes, "minute")
        }

        return "few seconds ago"
    }

    private fun pluralize(strictlyPositiveCount: Long, singular: String, plural: String = singular + "s"): String {
        if (strictlyPositiveCount == 1L) {
            return "1 $singular ago"
        }
        return "$strictlyPositiveCount $plural ago"
    }
}
