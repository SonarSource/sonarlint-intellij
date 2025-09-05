package org.sonarlint.intellij.config.global

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.util.SonarLintUtils

class SonarLintGlobalSettingsStoreTests : AbstractSonarLintLightTests() {

    companion object {

        @JvmStatic
        @BeforeAll
        fun setUpConfigFile() {
            val sonarlintConfigFile = PathManager.getOptionsFile("sonarlint")
            val testFile = SonarLintGlobalSettingsStoreTests::class.java.getResourceAsStream(
                "/options/sonarlint.xml")!!
            Files.copy(testFile, sonarlintConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        @JvmStatic
        @AfterAll
        fun deleteConfigFile() {
            PathManager.getOptionsFile("sonarlint").delete()
        }
    }

    @Test
    fun `should migrate credentials to credentials store`() { // todo check that password safe is test only!!!
        val serverConnections = SonarLintUtils.getService(SonarLintGlobalSettingsStore::class.java)
            ?.state?.serverConnections

        val passwordSafe = PasswordSafe.instance

        assertThat(getToken(passwordSafe, "noCredentials")).isNull()
        assertThat(getToken(passwordSafe, "onlyToken")).isEqualTo("onlyTokenToken")
        assertThat(getToken(passwordSafe, "onlyLoginPass")).isNull()
        assertThat(getToken(passwordSafe, "onlyToken")).isEqualTo("allCredentialsToken")

        assertThat(getCredentials(passwordSafe, "noCredentials")).isNull()
        assertThat(getCredentials(passwordSafe, "onlyToken")).isNull()
        assertThat(getCredentials(passwordSafe, "onlyLoginPass"))
            .isEqualTo(Credentials("onlyLoginPassLogin", "onlyLoginPassPassword"))
        assertThat(getCredentials(passwordSafe, "allCredentials"))
            .isEqualTo(Credentials("allCredentialsLogin", "allCredentialsPassword"))

        assertThat(serverConnections)
            .isNotNull()
            .contains(*connectionsWithoutCredentials())
    }

    private fun connectionsWithoutCredentials(): Array<out ServerConnection?> = arrayOf(
        ServerConnection.newBuilder()
            .setName("noCredentials")
            .setRegion("region")
            .setHostUrl("host")
            .setEnableProxy(true)
            .setOrganizationKey("organizationKey")
            .setDisableNotifications(true)
            .build(),
        ServerConnection.newBuilder()
            .setName("onlyToken")
            .setHostUrl("host")
            .build(),
        ServerConnection.newBuilder()
            .setName("onlyLoginPass")
            .setHostUrl("host")
            .build(),
        ServerConnection.newBuilder()
            .setName("allCredentials")
            .setHostUrl("host")
            .build()
    )

    private fun getToken(passwordSafe: PasswordSafe, name: String): String? = passwordSafe.getPassword(
        CredentialAttributes(
            generateServiceName(
                "SonarLint",
                "server:$name:token"))
    )

    private fun getCredentials(passwordSafe: PasswordSafe, name: String): Credentials? = passwordSafe.get(
        CredentialAttributes(
            generateServiceName(
                "SonarLint",
                "server:$name:credentials"))
    )
}
