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
package org.sonarlint.intellij.its.tests

import com.google.protobuf.InvalidProtocolBufferException
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.keyboard
import com.sonar.orchestrator.Orchestrator
import com.sonar.orchestrator.build.MavenBuild
import com.sonar.orchestrator.container.Server
import com.sonar.orchestrator.locator.FileLocation
import com.sonar.orchestrator.locator.MavenLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editor
import org.sonarlint.intellij.its.fixtures.fileBrowserDialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jPasswordField
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.ItUtils
import org.sonarlint.intellij.its.utils.OrchestratorUtils
import org.sonarlint.intellij.its.utils.optionalStep
import org.sonarqube.ws.client.HttpConnector
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.WsClientFactories
import org.sonarqube.ws.client.hotspots.SearchRequest
import org.sonarqube.ws.client.users.CreateRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest
import java.io.File
import java.net.URL

const val PROJECT_KEY = "sample-java-hotspot"

class OpenInIdeTest : BaseUiTest() {

    @Test
    fun opensHotspotAfterConfiguringConnectionAndBinding() = uiTest {
        openExistingProject("sample-java-hotspot", true)

        triggerOpenHotspotRequest()

        createConnection(this)
        bindRecentProject(this)
        verifyHotspotOpened(this)
    }

    private fun createConnection(robot: RemoteRobot) {
        with(robot) {
            idea {
                dialog("Opening Security Hotspot...") {
                    button("Create connection").click()
                }
                dialog("New Connection: Server Details") {
                    keyboard { enterText("Orchestrator") }
                    button("Next").click()
                }
                dialog("New Connection: Authentication") {
                    jPasswordField().text = token
                    button("Next").click()
                }
                dialog("New Connection: Configure Notifications") {
                    button("Next").click()
                }
                dialog("New Connection: Configuration completed") {
                    pressFinishOrCreate()
                }
            }
        }
    }

    private fun bindRecentProject(robot: RemoteRobot) {
        with(robot) {
            idea {
                dialog("Opening Security Hotspot...") {
                    button("Select project").click()
                }
                dialog("Select a project") {
                    button("Open or import").click()
                }
                fileBrowserDialog(arrayOf("Select Path")) {
                    selectProjectFile("sample-java-hotspot", true)
                }
                optionalStep {
                    dialog("Open Project") {
                        button("This Window").click()
                    }
                }
                dialog("Opening Security Hotspot...") {
                    button("Yes").click()
                }
            }
        }
    }

    private fun verifyHotspotOpened(robot: RemoteRobot) {
        verifyEditorOpened(robot)
        verifyToolWindowFilled(robot)
    }

    private fun verifyEditorOpened(robot: RemoteRobot) {
        with(robot) {
            idea {
                editor("Foo.java")
            }
        }
    }

    private fun verifyToolWindowFilled(robot: RemoteRobot) {
        with(robot) {
            idea {
                toolWindow("SonarLint") {
                    tab("Security Hotspots") {
                        content("SonarLintHotspotsPanel") {
                            assertThat(hasText("LOW")).isTrue()
                            assertThat(hasText("Make sure using this hardcoded IP address is safe here.")).isTrue()
                        }
                    }
                }
            }
        }
    }

    companion object {

        private var firstHotspotKey: String? = null
        lateinit var token: String

        private val ORCHESTRATOR: Orchestrator = OrchestratorUtils.defaultBuilderEnv()
            .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", ItUtils.javaVersion))
            .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"))
            .build()

        private const val SONARLINT_USER = "sonarlint"
        private const val SONARLINT_PWD = "sonarlintpwd"

        @BeforeAll
        @JvmStatic
        fun prepare() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClient()
            adminWsClient.users()
                .create(CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"))

            ORCHESTRATOR.server.provisionProject(PROJECT_KEY, "Sample Java")
            ORCHESTRATOR.server.associateProjectToQualityProfile(PROJECT_KEY, "java", "SonarLint IT Java Hotspot")

            // Build and analyze project to raise hotspot
            val file = File("projects/sample-java-hotspot/pom.xml")
            ORCHESTRATOR.executeBuild(
                MavenBuild.create(file)
                    .setCleanPackageSonarGoals()
                    .setProperty("sonar.login", SONARLINT_USER)
                    .setProperty("sonar.password", SONARLINT_PWD)
            )

            firstHotspotKey = getFirstHotspotKey(adminWsClient)
            val generateRequest = GenerateRequest()
            generateRequest.name = "TestUser"
            token = adminWsClient.userTokens().generate(generateRequest).token
        }

        private fun newAdminWsClient(): WsClient {
            val server = ORCHESTRATOR.server
            return WsClientFactories.getDefault().newClient(
                HttpConnector.newBuilder()
                    .url(server.url)
                    .credentials(Server.ADMIN_LOGIN, Server.ADMIN_PASSWORD)
                    .build()
            )
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }

        private fun triggerOpenHotspotRequest() {
            URL("http://localhost:64120/sonarlint/api/hotspots/show?project=$PROJECT_KEY&hotspot=$firstHotspotKey&server=${ORCHESTRATOR.server.url}")
                .readText()
        }
    }

}

@Throws(InvalidProtocolBufferException::class)
private fun getFirstHotspotKey(client: WsClient): String? {
    val searchRequest = SearchRequest()
    searchRequest.projectKey = PROJECT_KEY
    val searchResults = client.hotspots().search(searchRequest)
    val hotspot = searchResults.hotspotsList[0]
    return hotspot.key
}
