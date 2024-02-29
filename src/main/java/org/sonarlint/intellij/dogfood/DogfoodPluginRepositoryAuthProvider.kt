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
package org.sonarlint.intellij.dogfood

import com.intellij.ide.plugins.auth.PluginRepositoryAuthProvider
import com.intellij.notification.NotificationType
import com.intellij.util.io.inputStreamIfExists
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Paths
import java.util.Base64
import java.util.Properties
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.util.runOnPooledThread


var dogfoodUsername: String? = null
var dogfoodPassword: String? = null
var connectionFailed = false
var notificationAlreadyDisplayed = false

fun loadCredentials() {
    val inputStream = getSonarlintSystemPath().resolve("dogfood.properties").inputStreamIfExists()
    if (inputStream != null) {
        val props = Properties()
        props.load(inputStream)
        dogfoodUsername = props.getProperty("username")
        dogfoodPassword = props.getProperty("password")
    } else {
        dogfoodUsername = null
        dogfoodPassword = null
    }
}

fun resetTries() {
    connectionFailed = false
    notificationAlreadyDisplayed = false
}

private fun getSonarlintSystemPath() = Paths.get(System.getProperty("user.home")).resolve(".sonarlint")

class DogfoodPluginRepositoryAuthProvider : PluginRepositoryAuthProvider {

    companion object {
        private const val DOGFOOD_URL = "https://repox.jfrog.io"
    }

    init {
        loadCredentials()
    }

    override fun getAuthHeaders(url: String): Map<String, String> {
        if (!connectionFailed && !notificationAlreadyDisplayed) {
            return if (dogfoodUsername != null && dogfoodPassword != null) {
                val encodedAuth = "Basic " + "$dogfoodUsername:$dogfoodPassword".encodeBase64()

                val testUrlRepox = URL("https://repox.jfrog.io/repox/sonarsource")
                runOnPooledThread {
                    with(testUrlRepox.openConnection() as HttpURLConnection) {
                        requestMethod = "GET"
                        setRequestProperty("Authorization", encodedAuth)
                        connectTimeout = 10_000
                        connect()

                        if (responseCode == 401) {
                            SonarLintProjectNotifications.projectLessNotification(
                                "Dogfooding credentials are not valid",
                                "Connection to Repox was unauthorized, make sure the credentials 'username' and 'password' are valid in ~/.sonarlint/dogfood.properties",
                                NotificationType.WARNING,
                                DogfoodSetCredentialsAction()
                            )
                            connectionFailed = true
                        }
                    }
                }

                mapOf("Authorization" to encodedAuth)
            } else {
                SonarLintProjectNotifications.projectLessNotification(
                    "",
                    "Dogfooding credentials to Repox are missing in ~/.sonarlint/dogfood.properties",
                    NotificationType.WARNING,
                    DogfoodSetCredentialsAction()
                )
                notificationAlreadyDisplayed = true
                emptyMap()
            }
        }
        return emptyMap()
    }

    override fun canHandle(url: String): Boolean {
        return url.startsWith(DOGFOOD_URL)
    }

    private fun String.encodeBase64() = Base64.getEncoder().encodeToString(encodeToByteArray())

}