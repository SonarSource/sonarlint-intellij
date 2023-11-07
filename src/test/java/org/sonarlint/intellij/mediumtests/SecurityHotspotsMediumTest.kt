/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import java.nio.file.Path
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.persistence.FindingsCache
import org.sonarlint.intellij.mediumtests.fixtures.MockServer
import org.sonarlint.intellij.mediumtests.fixtures.StorageFixture.newStorage
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage
import org.sonarsource.sonarlint.core.commons.api.TextRange
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots

class SecurityHotspotsMediumTest : AbstractSonarLintLightTests() {
    private lateinit var storageFolderPath: Path
    private lateinit var mockServer: MockServer

    @BeforeEach
    fun prepare() {
        storageFolderPath = Paths.get(PathManager.getSystemPath()).resolve("sonarlint")
        mockServer = MockServer()
        mockServer.start()
        getService(project, FindingsCache::class.java).clearAllFindingsForAllFiles()
        getService(BackendService::class.java).projectOpened(project)
        getService(BackendService::class.java).modulesAdded(project, listOf(module))
        connectProjectTo(mockServer.url(""), "connection", "projectKey")
    }

    @AfterEach
    fun cleanUp() {
        mockServer.shutdown()
        getService(BackendService::class.java).projectClosed(project)
    }

    @Test
    // TODO re-enable
    @Disabled("Does not pass due to missing sync")
    fun should_raise_new_security_hotspots_when_connected_to_compatible_sonarqube() {
        createStorage(serverVersion = "9.7", activeRuleKey = "ruby:S1313")

        val raisedHotspots = openAndAnalyzeFile(filePath = "file.rb", codeSnippet = "ip = \"192.168.12.42\";")

        assertThat(raisedHotspots).extracting({ it.ruleKey },
            { it.message },
            { it.vulnerabilityProbability },
            { it.serverFindingKey },
            { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } },
            { it.introductionDate },
            { it.isOnNewCode() }).containsExactly(
            tuple(
                "ruby:S1313", "Make sure using this hardcoded IP address is safe here.", VulnerabilityProbability.LOW, null, Pair(5, 20),
                // no creation date on new hotspots
                null,
                true
            )
        )
    }

    @Test
    fun should_not_raise_any_security_hotspots_when_connected_to_incompatible_sonarqube() {
        createStorage(serverVersion = "9.6", activeRuleKey = "ruby:S1313")

        val raisedHotspots = openAndAnalyzeFile(filePath = "file.rb", codeSnippet = "ip = \"192.168.12.42\";")

        assertThat(raisedHotspots).isEmpty()
    }

    @Test
    fun should_not_raise_any_security_hotspots_when_server_version_is_unknown() {
        createStorage(serverVersion = null, activeRuleKey = "ruby:S1313")

        val raisedHotspots = openAndAnalyzeFile(filePath = "file.rb", codeSnippet = "ip = \"192.168.12.42\";")

        assertThat(raisedHotspots).isEmpty()
    }

    @Test
    // TODO re-enable
    @Disabled("Does not pass due to missing sync")
    fun should_keep_same_creation_date_when_matching_previous_security_hotspot() {
        ensureSecurityHotspotRaised(filePath = "file.rb", codeSnippet = "ip = \"192.168.12.42\";")

        val raisedHotspot = openAndAnalyzeFile(filePath = "file.rb", codeSnippet = "ip = \"192.168.12.42\";").first()

        assertThat(raisedHotspot.introductionDate).isNull()
    }

    @Test
    // TODO re-enable
    @Disabled("Does not pass due to missing sync")
    fun should_set_creation_date_when_raising_a_new_security_hotspot_in_an_already_analyzed_file() {
        ensureSecurityHotspotRaised(filePath = "file.rb")

        val newlyIntroducedHotspot = typeAndAnalyze(codeSnippetToAppend = "\nip2 = \"192.168.12.43\";")

        assertThat(newlyIntroducedHotspot.introductionDate).isNotNull
    }

    @Test
    // TODO re-enable
    @Disabled("Does not pass due to missing sync")
    fun should_match_security_hotspot_previously_detected_on_the_server() {
        prepareStorageAndServer(
            serverSecurityHotspot = ServerSecurityHotspot(
                filePath = "file.rb",
                key = "hotspotKey",
                message = "Make sure using this hardcoded IP address is safe here.",
                ruleKey = "ruby:S1313",
                textRange = TextRange(1, 5, 1, 20),
                introductionDate = "2020-09-21T12:46:39+0000",
                status = "TO_REVIEW",
                vulnerabilityProbability = "LOW"
            )
        )

        val raisedHotspot = openAndAnalyzeFile(filePath = "file.rb", codeSnippet = "ip = \"192.168.12.42\";").first()

        assertThat(raisedHotspot).extracting({ it.serverFindingKey },
            { it.isResolved },
            { it.introductionDate },
            { it.vulnerabilityProbability }).containsExactly("hotspotKey", false, 1600692399000L, VulnerabilityProbability.LOW)
    }

    @Test
    fun should_not_raise_issue_when_fixed_in_the_code() {
        ensureSecurityHotspotRaised(filePath = "file.rb")

        val raisedHotspots = openAndAnalyzeFile(filePath = "file.rb", codeSnippet = "")

        assertThat(raisedHotspots).isEmpty()
    }

    private fun ensureSecurityHotspotRaised(filePath: String, codeSnippet: String = "ip = \"192.168.12.42\";") {
        createStorage(activeRuleKey = "ruby:S1313")
        openAndAnalyzeFile(filePath = filePath, codeSnippet = codeSnippet)
    }

    private fun prepareStorageAndServer(
        serverSecurityHotspot: ServerSecurityHotspot,
        branchName: String = "master",
    ) {
        createStorage(activeRuleKey = serverSecurityHotspot.ruleKey, projectKey = serverSecurityHotspot.projectKey, branchName = branchName)
        getService(BackendService::class.java).didVcsRepoChange(module.project)
        mockServer.addProtobufResponse(
            "/api/hotspots/search.protobuf?projectKey=${serverSecurityHotspot.projectKey}&files=${serverSecurityHotspot.filePath}&branch=$branchName&ps=500&p=1",
            Hotspots.SearchWsResponse.newBuilder().setPaging(Common.Paging.newBuilder().setTotal(1).build()).addHotspots(
                Hotspots.SearchWsResponse.Hotspot.newBuilder().setKey(serverSecurityHotspot.key).setMessage(serverSecurityHotspot.message)
                    .setRuleKey(serverSecurityHotspot.ruleKey).setStatus(serverSecurityHotspot.status)
                    .setVulnerabilityProbability(serverSecurityHotspot.vulnerabilityProbability)
                    .setCreationDate(serverSecurityHotspot.introductionDate).setComponent(serverSecurityHotspot.componentKey).setTextRange(
                        Common.TextRange.newBuilder().setStartLine(serverSecurityHotspot.textRange.startLine)
                            .setStartOffset(serverSecurityHotspot.textRange.startLineOffset)
                            .setEndLine(serverSecurityHotspot.textRange.endLine).setEndOffset(serverSecurityHotspot.textRange.endLineOffset)
                            .build()
                    ).build()
            ).addComponents(
                Hotspots.Component.newBuilder().setKey(serverSecurityHotspot.componentKey).setPath(serverSecurityHotspot.filePath).build()
            ).build()
        )
    }

    private fun createStorage(
        serverVersion: String? = "9.9",
        projectKey: String = "projectKey",
        branchName: String = "master",
        activeRuleKey: String,
    ) {
        newStorage("connection").withServerVersion(serverVersion).withProject(projectKey) { project ->
            project.withRuleSet(SonarLanguage.RUBY.sonarLanguageKey) { ruleSet -> ruleSet.withActiveRule(activeRuleKey, "BLOCKER") }
                .withMainBranchName(branchName)
        }.create(storageFolderPath)
    }


    private fun openAndAnalyzeFile(filePath: String, codeSnippet: String): Collection<LiveSecurityHotspot> {
        val fileToAnalyze = myFixture.configureByText(filePath, "$codeSnippet<caret>").virtualFile
        val submitter = getService(project, AnalysisSubmitter::class.java)
        submitter.analyzeFilesPreCommit(listOf(fileToAnalyze))
        return getService(project, FindingsCache::class.java).getSecurityHotspotsForFile(fileToAnalyze)
    }

    private fun typeAndAnalyze(codeSnippetToAppend: String): LiveSecurityHotspot {
        val previousCaretOffset = myFixture.caretOffset
        myFixture.type(codeSnippetToAppend)
        val file = myFixture.file.virtualFile
        getService(project, AnalysisSubmitter::class.java).analyzeFilesPreCommit(listOf(file))
        return getService(project, FindingsCache::class.java).getSecurityHotspotsForFile(file).first { it.range!!.startOffset > previousCaretOffset }
    }

    private data class ServerSecurityHotspot(
        val projectKey: String = "projectKey",
        val filePath: String,
        val key: String,
        val message: String,
        val ruleKey: String,
        val textRange: TextRange,
        val introductionDate: String,
        val status: String,
        val vulnerabilityProbability: String,
    ) {
        val componentKey = "$projectKey:$filePath"
    }
}
