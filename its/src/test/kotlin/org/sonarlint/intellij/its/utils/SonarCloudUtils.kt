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
package org.sonarlint.intellij.its.utils

import com.intellij.remoterobot.utils.waitFor
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions
import org.sonarqube.ws.MediaTypes
import org.sonarqube.ws.client.GetRequest
import org.sonarqube.ws.client.HttpConnector
import org.sonarqube.ws.client.PostRequest
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.WsClientFactories
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

class SonarCloudUtils {

    companion object {

        const val SONARCLOUD_STAGING_URL = "https://sc-staging.io"
        const val SONARCLOUD_ORGANIZATION = "sonarlint-it"
        private const val SONARCLOUD_USER = "sonarlint-it"

        fun newAdminSonarCloudWsClientWithUser(scUrl: String): WsClient {
            val sonarCloudPassword = System.getenv("SONARCLOUD_IT_PASSWORD")
            return WsClientFactories.getDefault().newClient(
                HttpConnector.newBuilder()
                    .url(scUrl)
                    .credentials(SONARCLOUD_USER, sonarCloudPassword)
                    .build()
            )
        }

        fun analyzeSonarCloudWithMaven(adminWsClient: WsClient, projectKey: String, projectDirName: String, token: String) {
            val projectDir = Paths.get("projects/$projectDirName").toAbsolutePath()
            runMaven(
                projectDir, "clean", "package", "sonar:sonar",
                "-Dsonar.projectKey=$projectKey",
                "-Dsonar.host.url=$SONARCLOUD_STAGING_URL",
                "-Dsonar.organization=$SONARCLOUD_ORGANIZATION",
                "-Dsonar.token=$token",
                "-Dsonar.scm.disabled=true",
                "-Dsonar.branch.autoconfig.disabled=true"
            )

            val request = GetRequest("api/analysis_reports/is_queue_empty")
            waitFor(Duration.ofMinutes(1), interval = Duration.ofSeconds(5)) {
                adminWsClient.wsConnector().call(request).use { response ->
                    "true" == response.content()
                }
            }
        }

        fun restoreSonarCloudProfile(adminWsClient: WsClient, fileName: String) {
            val backupFile = File("src/test/resources/$fileName")
            val request = PostRequest("api/qualityprofiles/restore")
            request.setParam("organization", SONARCLOUD_ORGANIZATION)
            request.setPart("backup", PostRequest.Part(MediaTypes.XML, backupFile))
            adminWsClient.wsConnector().call(request)
        }

        fun provisionSonarCloudProfile(adminWsClient: WsClient, projectName: String, projectKey: String) {
            val request = PostRequest("api/projects/create")
            request.setParam("name", projectName)
            request.setParam("project", projectKey)
            request.setParam("organization", SONARCLOUD_ORGANIZATION)
            adminWsClient.wsConnector().call(request)
        }

        fun associateSonarCloudProjectToQualityProfile(
            adminWsClient: WsClient,
            language: String,
            projectKey: String,
            qualityProfile: String,
        ) {
            val request = PostRequest("api/qualityprofiles/add_project")
            request.setParam("language", language)
            request.setParam("project", projectKey)
            request.setParam("qualityProfile", qualityProfile)
            request.setParam("organization", SONARCLOUD_ORGANIZATION)
            adminWsClient.wsConnector().call(request)
        }

        @Throws(IOException::class)
        private fun runMaven(workDir: Path, vararg args: String) {
            val cmdLine: CommandLine
            if (SystemUtils.IS_OS_WINDOWS) {
                cmdLine = CommandLine.parse("cmd.exe")
                cmdLine.addArguments("/c")
                cmdLine.addArguments("mvn")
            } else {
                cmdLine = CommandLine.parse("mvn")
            }

            cmdLine.addArguments(arrayOf("--batch-mode", "--show-version", "--errors"))
            cmdLine.addArguments(args)
            val executor = DefaultExecutor()
            executor.workingDirectory = workDir.toFile()
            val exitValue = executor.execute(cmdLine)
            Assertions.assertThat(exitValue).isZero()
        }

    }

}