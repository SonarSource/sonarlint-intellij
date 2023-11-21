package org.sonarlint.intellij.config.global

class ServerConnectionCredentialsNotFound(connectionName: String) : RuntimeException("Unable to load credentials for connection '$connectionName'")
