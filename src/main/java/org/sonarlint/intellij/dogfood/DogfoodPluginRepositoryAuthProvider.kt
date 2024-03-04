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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.util.runOnPooledThread

var connectionFailed = false
var notificationAlreadyDisplayed = false

fun resetTries() {
    connectionFailed = false
    notificationAlreadyDisplayed = false
}

class DogfoodPluginRepositoryAuthProvider : PluginRepositoryAuthProvider {

    companion object {
        private const val DOGFOOD_URL = "https://repox.jfrog.io"
    }

    override fun getAuthHeaders(url: String): Map<String, String> {
        if (!connectionFailed && !notificationAlreadyDisplayed) {
            val dogfoodCredentials = getService(DogfoodCredentialsStore::class.java).state
            return if (dogfoodCredentials.username != null && dogfoodCredentials.password != null) {
                val encodedAuth = "Basic " + "${dogfoodCredentials.username}:${dogfoodCredentials.password}".encodeBase64()

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
                                "Connection to Repox was unauthorized, make sure the credentials 'username' and 'password' are valid",
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
                    "Dogfooding credentials to Repox are missing",
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