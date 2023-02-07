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
package org.sonarlint.intellij.mediumtests

import com.intellij.openapi.application.PathManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Before
import org.junit.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.persistence.FindingsCache
import org.sonarlint.intellij.mediumtests.fixtures.StorageFixture.newStorage
import org.sonarsource.sonarlint.core.commons.Language
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability
import org.sonarsource.sonarlint.core.serverconnection.FileUtils
import java.nio.file.Path
import java.nio.file.Paths

class SecurityHotspotsMediumTest : AbstractSonarLintLightTests() {
    private lateinit var storageFolderPath: Path

    @Before
    fun prepare() {
        storageFolderPath = Paths.get(PathManager.getSystemPath()).resolve("sonarlint")
        FileUtils.deleteRecursively(storageFolderPath)
        engineManager.stopAllEngines(false)
        connectProjectTo("http://url", "connection", "projectKey")
    }

    @Test
    fun should_raise_new_security_hotspots_when_connected_to_compatible_sonarqube() {
        createStorage(serverVersion = "9.7", activeRuleKey = "ruby:S1313")

        val hotspots = analyze("ip = \"192.168.12.42\";")

        assertThat(hotspots).extracting({ it.ruleKey },
                { it.message },
                { it.vulnerabilityProbability },
                { it.serverFindingKey },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } }).containsExactly(
                tuple(
                    "ruby:S1313", "Make sure using this hardcoded IP address is safe here.", VulnerabilityProbability.LOW, null, Pair(5, 20)
                )
            )
    }

    @Test
    fun should_not_raise_any_security_hotspots_when_connected_to_incompatible_sonarqube() {
        createStorage(serverVersion = "9.6", activeRuleKey = "ruby:S1313")

        val hotspots = analyze("ip = \"192.168.12.42\";")

        assertThat(hotspots).isEmpty()
    }

    @Test
    fun should_not_raise_any_security_hotspots_when_server_version_is_unknown() {
        createStorage(serverVersion = null, activeRuleKey = "ruby:S1313")

        val hotspots = analyze("ip = \"192.168.12.42\";")

        assertThat(hotspots).isEmpty()
    }

    private fun createStorage(
        serverVersion: String? = "9.7",
        activeRuleKey: String
    ) {
        newStorage("connection").withServerVersion(serverVersion)
            .withProject("projectKey") { project ->
                project.withRuleSet(Language.RUBY.languageKey) { ruleSet -> ruleSet.withActiveRule(activeRuleKey, "BLOCKER") }
            }.create(storageFolderPath)
    }


    private fun analyze(codeSnippet: String): Collection<LiveSecurityHotspot> {
        val fileToAnalyze = myFixture.configureByText("file.rb", codeSnippet).virtualFile
        val submitter = getService(project, AnalysisSubmitter::class.java)
        submitter.analyzeFilesPreCommit(listOf(fileToAnalyze))
        return getService(project, FindingsCache::class.java).getSecurityHotspotsForFile(fileToAnalyze)
    }
}
