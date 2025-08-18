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
package org.sonarlint.intellij.finding.sca

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.messages.ProjectConfigurationListener
import org.sonarlint.intellij.util.runOnPooledThread

@Service(Service.Level.PROJECT)
class DependencyRisksRefreshTrigger(private val project: Project) {
  fun subscribeToTriggeringEvents() {
    val busConnection = project.messageBus.connect()
    with(busConnection) {
      subscribe(ProjectConfigurationListener.TOPIC, ProjectConfigurationListener { triggerRefresh() })
      subscribe(GlobalConfigurationListener.TOPIC, object : GlobalConfigurationListener.Adapter() {
        override fun applied(previousSettings: SonarLintGlobalSettings, newSettings: SonarLintGlobalSettings) {
          triggerRefresh()
        }
      })
    }
    triggerRefresh()
  }

  fun triggerRefresh() {
    runOnPooledThread(project) {
      getService(project, DependencyRisksPresenter::class.java).checkIfSupported()
    }
  }

}
