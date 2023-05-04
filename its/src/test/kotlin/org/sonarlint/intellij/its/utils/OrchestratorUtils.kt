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
package org.sonarlint.intellij.its.utils

import com.sonar.orchestrator.Orchestrator
import com.sonar.orchestrator.OrchestratorBuilder
import com.sonar.orchestrator.build.MavenBuild
import com.sonar.orchestrator.build.SonarScanner
import com.sonar.orchestrator.container.Server
import org.sonarqube.ws.client.HttpConnector
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.WsClientFactories
import org.sonarqube.ws.client.users.CreateRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest
import java.io.File

class OrchestratorUtils {
    companion object {
        private const val SONARLINT_USER = "sonarlint"
        private const val SONARLINT_PWD = "sonarlintpwd"

        fun defaultBuilderEnv(): OrchestratorBuilder {
            return Orchestrator.builderEnv()
                .defaultForceAuthentication()
                .useDefaultAdminCredentialsForBuilds(true)
                .setSonarVersion(ItUtils.SONAR_VERSION)
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

            return wsClient;
        }

        fun executeBuildWithMaven(filePath: String, orchestrator: Orchestrator) {
            orchestrator.executeBuild(
                MavenBuild.create(File(filePath))
                    .setCleanPackageSonarGoals()
                    .setProperty("sonar.login", SONARLINT_USER)
                    .setProperty("sonar.password", SONARLINT_PWD)
            )
        }

        fun executeBuildWithSonarScanner(filePath: String, orchestrator: Orchestrator, projectKey: String) {
            orchestrator.executeBuild(
                SonarScanner.create(File(filePath))
                    .setProperty("sonar.login", SONARLINT_USER)
                    .setProperty("sonar.password", SONARLINT_PWD)
                    .setProperty("sonar.projectKey", projectKey)
            )
        }

        fun generateToken(wsClient: WsClient, userName: String) : String {
            val generateRequest = GenerateRequest()
            generateRequest.name = "TestUser_$userName"
            return wsClient.userTokens().generate(generateRequest).token
        }
    }
}
