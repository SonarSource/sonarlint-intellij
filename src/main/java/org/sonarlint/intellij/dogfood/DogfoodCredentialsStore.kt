package org.sonarlint.intellij.dogfood

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "SonarLintDogfoodCredentials",
    storages = [Storage("sonarlint-dogfood.xml")]
)
@Service(Service.Level.APP)
class DogfoodCredentialsStore : PersistentStateComponent<DogfoodCredentials> {

    private var credentials = DogfoodCredentials()

    override fun getState(): DogfoodCredentials {
        return credentials
    }

    override fun loadState(credentials: DogfoodCredentials) {
        this.credentials = credentials
    }

    fun save(credentials: DogfoodCredentials) {
        this.credentials = credentials
    }

}