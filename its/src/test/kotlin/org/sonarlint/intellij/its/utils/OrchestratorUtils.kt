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
package org.sonarlint.intellij.its.utils

import com.sonar.orchestrator.build.MavenBuild
import com.sonar.orchestrator.build.SonarScanner
import com.sonar.orchestrator.container.Server
import com.sonar.orchestrator.junit5.OrchestratorExtension
import com.sonar.orchestrator.junit5.OrchestratorExtensionBuilder
import java.io.File
import org.sonarlint.intellij.its.utils.ItUtils.SONAR_VERSION
import org.sonarqube.ws.client.HttpConnector
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.WsClientFactories
import org.sonarqube.ws.client.users.CreateRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest

class OrchestratorUtils {

    companion object {
        private const val SONARLINT_USER = "sonarlint"
        private const val SONARLINT_PWD = "sonarlintpwd"

        fun defaultBuilderEnv(): OrchestratorExtensionBuilder {
            return OrchestratorExtension.builderEnv()
                .defaultForceAuthentication()
                .useDefaultAdminCredentialsForBuilds(true)
                .setSonarVersion(SONAR_VERSION)
        }

        fun newAdminWsClientWithUser(server: Server): WsClient {
            val wsClient = WsClientFactories.getDefault().newClient(
                HttpConnector.newBuilder()
                    .url(server.url)
                    .credentials(Server.ADMIN_LOGIN, Server.ADMIN_PASSWORD)
                    .build()
            )
            wsClient.users()
                .create(CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"))

            return wsClient
        }

        fun executeBuildWithMaven(filePath: String, orchestrator: OrchestratorExtension) {
            var mavenBuild = MavenBuild.create(File(filePath))
                .setCleanPackageSonarGoals()

            mavenBuild = if (orchestrator.server.version().isGreaterThanOrEquals(10, 2)) {
                mavenBuild.setProperty("sonar.token", orchestrator.defaultAdminToken)
            } else {
                mavenBuild.setProperty("sonar.login", Server.ADMIN_LOGIN)
                    .setProperty("sonar.password", Server.ADMIN_PASSWORD)
            }

            orchestrator.executeBuild(mavenBuild)
        }

        fun executeBuildWithSonarScanner(filePath: String, orchestrator: OrchestratorExtension, projectKey: String) {
            var scanner = SonarScanner.create(File(filePath))
                    .setProperty("sonar.projectKey", projectKey)

            scanner = if (orchestrator.server.version().isGreaterThanOrEquals(10, 2)) {
                scanner.setProperty("sonar.token", orchestrator.defaultAdminToken)
            } else {
                scanner.setProperty("sonar.login", Server.ADMIN_LOGIN)
                    .setProperty("sonar.password", Server.ADMIN_PASSWORD)
            }

            orchestrator.executeBuild(scanner)
        }

        fun generateTokenNameAndValue(wsClient: WsClient, userName: String): Pair<String, String> {
            val generateRequest = GenerateRequest()
            generateRequest.name = "TestUser_$userName"
            val response = wsClient.userTokens().generate(generateRequest)
            return Pair(response.name, response.token)
        }
    }

}
