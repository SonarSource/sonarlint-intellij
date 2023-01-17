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
package org.sonarlint.intellij.core.server.events

import com.intellij.openapi.project.Project
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.ProjectBinding
import org.sonarlint.intellij.messages.ProjectBindingListener

class SubscribeOnProjectBindingChange(project: Project) : ProjectBindingListener {
    override fun bindingChanged(previousBinding: ProjectBinding?, newBinding: ProjectBinding?) {
        if (previousBinding != null && previousBinding.connectionName != newBinding?.connectionName) {
            // we need to resubscribe for the previous engine as the project keys might have changed
            getService(ServerEventsService::class.java).autoSubscribe(previousBinding)
        }
        if (newBinding != null) {
            getService(ServerEventsService::class.java).autoSubscribe(newBinding)
        }
    }
}
