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
package org.sonarlint.intellij.config.global.credentials

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

private const val SUBSYSTEM = "SonarLint"

fun PasswordSafe.getToken(name: String): String? {
    return getPassword(tokenAttributes(name))
}

fun PasswordSafe.getUsernamePassword(name: String): Credentials? {
    return get(usernamePasswordAttributes(name))
}

fun PasswordSafe.getDogfoodCredentials(): Credentials? {
    return get(dogfoodAttributes())
}

fun PasswordSafe.eraseToken(name: String) {
    setToken(name, null)
}

fun PasswordSafe.eraseUsernamePassword(name: String) {
    setUsernamePassword(name, null)
}

fun PasswordSafe.eraseDogfoodCredentials() {
    set(dogfoodAttributes(), null)
}

fun PasswordSafe.setToken(name: String, value: String?) {
    setPassword(tokenAttributes(name), value)
}

fun PasswordSafe.setUsernamePassword(name: String, value: Credentials?) {
    set(usernamePasswordAttributes(name), value)
}

fun PasswordSafe.setUsernamePassword(name: String, username: String?, password: String?) {
    setUsernamePassword(name, Credentials(username, password))
}

fun PasswordSafe.setDogfoodUsernamePassword(username: String?, password: String?) {
    set(dogfoodAttributes(), Credentials(username, password))
}

private fun tokenAttributes(name: String): CredentialAttributes = CredentialAttributes(
    serviceName(name, "token")
)

private fun usernamePasswordAttributes(name: String): CredentialAttributes = CredentialAttributes(
    serviceName(name, "usernamePassword")
)

private fun dogfoodAttributes() = CredentialAttributes(
    generateServiceName(SUBSYSTEM, "dogfood:usernamePassword")
)

private fun serviceName(name: String, credentialsType: String): String = generateServiceName(
    SUBSYSTEM, "server:$name:$credentialsType"
)
