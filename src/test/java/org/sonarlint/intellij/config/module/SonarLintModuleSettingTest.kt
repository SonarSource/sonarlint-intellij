package org.sonarlint.intellij.config.module

import org.assertj.core.api.Assertions
import org.junit.Test

class SonarLintModuleSettingTest {

    @Test
    fun bindingRoundTrip() {
        val settings = SonarLintModuleSettings()
        Assertions.assertThat(settings.projectKey).isNull()
        Assertions.assertThat(settings.isBound).isFalse

        settings.bindTo("project1")
        Assertions.assertThat(settings.projectKey).isEqualTo("project1")
        Assertions.assertThat(settings.isBound).isTrue

        settings.unbind()
        Assertions.assertThat(settings.projectKey).isNull()
        Assertions.assertThat(settings.isBound).isFalse
    }
}
