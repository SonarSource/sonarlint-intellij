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
package org.sonarlint.intellij.notifications

import org.sonarsource.sonarlint.core.client.api.notifications.ServerNotification
import java.time.ZonedDateTime

fun aServerNotification(category: String, message: String, link: String, projectKey: String, time: ZonedDateTime = ZonedDateTime.now()) : ServerNotification {
  return object: ServerNotification {
    override fun category() = category
    override fun message() = message
    override fun link() = link
    override fun projectKey() = projectKey
    override fun time() = time
  }
}
