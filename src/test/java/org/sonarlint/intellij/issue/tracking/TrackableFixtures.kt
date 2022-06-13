/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.issue.tracking

fun aTrackable() = object : Trackable {
    override fun getLine() = 5
    override fun getMessage() = "msg"
    override fun getTextRangeHash() = 4
    override fun getLineHash() = 3
    override fun getRuleKey() = "ruleKey"
    override fun getServerIssueKey() = "serverKey"
    override fun getCreationDate() = 1000L
    override fun isResolved() = false
    override fun getSeverity() = "severity"
    override fun getType() = "type"
}
