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
package org.sonarlint.intellij.finding.issue.risks

import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID
import org.sonarlint.intellij.finding.Issue
import org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.client.utils.SoftwareQuality
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType

// todo move to org.sonarlint.intellij.finding.sca
class LocalDependencyRisk(private val remoteDependencyRisk: DependencyRiskDto) : Issue {

    override fun resolve() {
        TODO("Not yet implemented")
    }

    override fun reopen() {
        TODO("Not yet implemented")
    }

    override fun getId(): UUID = remoteDependencyRisk.id

    override fun getCleanCodeAttribute(): CleanCodeAttribute? {
        TODO("Not yet implemented")
    }

    override fun getImpacts(): List<ImpactDto> {
        TODO("Not yet implemented")
    }

    override fun getHighestQuality(): SoftwareQuality? {
        TODO("Not yet implemented")
    }

    override fun getHighestImpact(): ImpactSeverity? {
        TODO("Not yet implemented")
    }

    override fun getServerKey(): String? {
        TODO("Not yet implemented")
    }

    override fun getRuleKey(): String = "${remoteDependencyRisk.packageName} ${remoteDependencyRisk.packageVersion}"

    override fun getType(): RuleType? {
        TODO("Not yet implemented")
    }

    override fun getRuleDescriptionContextKey(): String? {
        TODO("Not yet implemented")
    }

    override fun file(): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun isValid(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isOnNewCode(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isResolved(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isAiCodeFixable(): Boolean {
        TODO("Not yet implemented")
    }
}
