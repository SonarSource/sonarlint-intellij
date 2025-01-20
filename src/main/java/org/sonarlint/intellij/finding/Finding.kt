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
package org.sonarlint.intellij.finding

import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID
import org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.client.utils.SoftwareQuality
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType

interface Finding {

    fun getId(): UUID
    fun getCleanCodeAttribute(): CleanCodeAttribute?
    fun getImpacts(): List<ImpactDto>
    fun getHighestQuality(): SoftwareQuality?
    fun getHighestImpact(): ImpactSeverity?
    fun getServerKey(): String?
    fun getRuleKey(): String
    fun getType(): RuleType?
    fun getRuleDescriptionContextKey(): String?
    fun file(): VirtualFile?
    fun isValid(): Boolean
    fun isOnNewCode(): Boolean
    fun isResolved(): Boolean

}
