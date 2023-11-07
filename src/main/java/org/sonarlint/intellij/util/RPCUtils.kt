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
package org.sonarlint.intellij.util

import com.google.common.base.Enums
import java.util.EnumSet
import org.sonarsource.sonarlint.core.commons.ImpactSeverity
import org.sonarsource.sonarlint.core.commons.IssueSeverity
import org.sonarsource.sonarlint.core.commons.RuleType
import org.sonarsource.sonarlint.core.commons.SoftwareQuality
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language
import org.sonarsource.sonarlint.core.commons.Language as commonLanguage
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity as rpcImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity as rpcIssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType as rpcRuleType


object RPCUtils {
    @JvmStatic
    fun mapLevel(level: LogLevel): ClientLogOutput.Level {
        return when (level.name) {
            ClientLogOutput.Level.ERROR.name -> {
                return ClientLogOutput.Level.ERROR
            }
            ClientLogOutput.Level.WARN.name -> {
                return ClientLogOutput.Level.WARN
            }
            ClientLogOutput.Level.INFO.name -> {
                return ClientLogOutput.Level.INFO
            }
            ClientLogOutput.Level.DEBUG.name -> {
                return ClientLogOutput.Level.DEBUG
            }
            ClientLogOutput.Level.TRACE.name -> {
                return ClientLogOutput.Level.TRACE
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    @JvmStatic
    fun mapRuleType(type: RuleType): rpcRuleType {
        return when {
            type.name === rpcRuleType.BUG.name -> {
                org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.BUG
            }
            type.name === rpcRuleType.CODE_SMELL.name -> {
                rpcRuleType.CODE_SMELL
            }
            type.name === rpcRuleType.SECURITY_HOTSPOT.name -> {
                rpcRuleType.SECURITY_HOTSPOT
            }
            type.name === rpcRuleType.VULNERABILITY.name -> {
                rpcRuleType.VULNERABILITY
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    @JvmStatic
    fun mapSeverity(severity: IssueSeverity): rpcIssueSeverity {
        when (severity.name) {
            IssueSeverity.BLOCKER.name -> {
                return rpcIssueSeverity.BLOCKER
            }
            IssueSeverity.CRITICAL.name -> {
                return rpcIssueSeverity.CRITICAL
            }
            IssueSeverity.INFO.name -> {
                return rpcIssueSeverity.INFO
            }
            IssueSeverity.MAJOR.name -> {
                return rpcIssueSeverity.MAJOR
            }
            IssueSeverity.MINOR.name -> {
                return rpcIssueSeverity.MINOR
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    @JvmStatic
    fun mapImpactSeverity(severity: ImpactSeverity): rpcImpactSeverity {
        when (severity.name) {
            ImpactSeverity.HIGH.name -> {
                return rpcImpactSeverity.HIGH
            }
            ImpactSeverity.MEDIUM.name -> {
                return rpcImpactSeverity.MEDIUM
            }
            ImpactSeverity.LOW.name -> {
                return rpcImpactSeverity.LOW
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    @JvmStatic
    fun dummyDefaultImpacts(): HashMap<SoftwareQuality, ImpactSeverity> {
        var map = hashMapOf(Pair(SoftwareQuality.SECURITY, ImpactSeverity.HIGH))
        return map
    }

    @JvmStatic
    fun mapLanguages(languages : Set<org.sonarsource.sonarlint.core.commons.Language>) : Set<Language>{
        var languageList: MutableList<Language?>? = java.util.ArrayList<Language?>()

        languages.forEach {
            languageList?.add(getSingleMappedLanguage(it))
        }

        return EnumSet.copyOf(languageList)
    }

    @JvmStatic
    fun getSingleMappedLanguage(it: commonLanguage) =
        Enums.getIfPresent(Language::class.java, it.name).orNull()

    @JvmStatic
    fun getSingleMappedLanguageWhenRPCInput(it: Language) =
        Enums.getIfPresent(commonLanguage::class.java, it.name).orNull()
}
