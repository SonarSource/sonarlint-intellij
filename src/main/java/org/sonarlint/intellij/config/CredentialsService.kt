package org.sonarlint.intellij.config

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto

@Service(Service.Level.APP)
class CredentialsService {

    fun getCredentials(connection: ServerConnection): Either<TokenDto, UsernamePasswordDto> {
        return if (connection.token != null) {
            Either.forLeft(TokenDto(connection.token!!))
        } else {
            Either.forRight(UsernamePasswordDto(connection.login, connection.password))
        }
    }

    fun migrate(connection: ServerConnection): ServerConnection {
        if (connection.token != null) {
            PasswordSafe.instance.setPassword(
                tokenAttributes(connection.name),
                connection.token)
        }
        if (connection.password != null) {
            PasswordSafe.instance.set(credentialsAttributes(connection.name),
                Credentials(connection.login, connection.password))
        }
        return ServerConnection.newBuilder()
            .setRegion(connection.region)
            .setHostUrl(connection.hostUrl)
            .setName(connection.name)
            .setEnableProxy(connection.isEnableProxy)
            .setOrganizationKey(connection.organizationKey)
            .setDisableNotifications(connection.isDisableNotifications)
            .build()
    }

    private fun tokenAttributes(name: String): CredentialAttributes = CredentialAttributes(
        serviceName(name, "token")
    )

    private fun credentialsAttributes(name: String): CredentialAttributes = CredentialAttributes(
        serviceName(name, "credentials")
    )

    private fun serviceName(name: String, type: String): String = generateServiceName(
        "SonarLint",
        "server:$name:$type"
    )
}
