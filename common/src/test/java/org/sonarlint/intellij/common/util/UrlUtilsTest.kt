package org.sonarlint.intellij.common.util

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

private const val BASE_URL = "http://localhost:8080/url"

class UrlUtilsTest {

    @Test
    fun should_not_change_url_when_no_params() {
        val same = UrlUtils.addParameters(BASE_URL, emptyMap())

        Assertions.assertThat(same).isSameAs(BASE_URL)
    }

    @Test
    fun should_add_params_where_there_are_none() {
        val withParameters = UrlUtils.addParameters(
            "$BASE_URL?", mapOf(
                "param1" to "value1",
                "param2" to "value2"
            )
        )

        Assertions.assertThat(withParameters).isEqualTo("$BASE_URL?param1=value1&param2=value2")
    }

    @Test
    fun should_preserve_existing_parameters() {
        val withParameters = UrlUtils.addParameters(
            "$BASE_URL?existingParam=existingValue", mapOf(
                "param1" to "value1",
                "param2" to "value2"
            )
        )

        Assertions.assertThat(withParameters).isEqualTo("$BASE_URL?existingParam=existingValue&param1=value1&param2=value2")
    }

    @Test
    fun should_append_to_existing_parameters() {
        val withParameters = UrlUtils.addParameters(
            "$BASE_URL?existingParam=existingValue", mapOf(
                "existingParam" to "value1",
                "param2" to "value2"
            )
        )

        Assertions.assertThat(withParameters).isEqualTo("$BASE_URL?existingParam=existingValue&existingParam=value1&param2=value2")
    }
}
