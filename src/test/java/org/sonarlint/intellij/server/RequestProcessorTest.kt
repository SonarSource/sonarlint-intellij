/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.server

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue
import io.netty.handler.codec.http.HttpMethod
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.eq
import org.sonarlint.intellij.issue.hotspot.SecurityHotspotShowRequestHandler
import org.sonarlint.intellij.messages.UserTokenListener

class RequestProcessorTest : AbstractSonarLintLightTests() {

    private lateinit var appInfoMock: ApplicationInfo
    lateinit var underTest: RequestProcessor
    private lateinit var showRequestHandler: SecurityHotspotShowRequestHandler
    private val badRequest = BadRequest("Invalid path or method.")

    @Before
    fun setup() {
        showRequestHandler = mock(SecurityHotspotShowRequestHandler::class.java)
        appInfoMock = mock(ApplicationInfo::class.java)
        underTest = RequestProcessor(appInfoMock, showRequestHandler)
    }

    @Test
    fun it_should_fail_if_method_is_not_get() {
        val request = Request("", HttpMethod.POST, false)

        val result = underTest.processRequest(request)

        assertThat(result).isEqualTo(badRequest)
    }

    @Test
    fun it_should_fail_if_endpoint_is_not_expected() {
        val request = Request("/unexpected/endpoint", HttpMethod.GET, false)

        val result = underTest.processRequest(request)

        assertThat(result).isEqualTo(badRequest)
    }

    @Test
    fun it_should_not_return_description_if_untrusted_origin() {
        val ideName = "Version name"
        val fullVersion = "Full version"
        val request = Request(STATUS_ENDPOINT, HttpMethod.GET, false)
        `when`(appInfoMock.versionName).thenReturn(ideName)
        `when`(appInfoMock.fullVersion).thenReturn(fullVersion)

        val result = underTest.processRequest(request)

        assertThat(result).isInstanceOf(Success::class.java)
        assertThat((result as Success).body).isEqualTo("{\"ideName\":\"Version name\",\"description\":\"\"}")
    }

    @Test
    fun it_should_return_status_for_status_endpoint_if_trusted_origin() {
        val ideName = "Version name"
        val fullVersion = "Full version"
        val request = Request(STATUS_ENDPOINT, HttpMethod.GET, true)
        `when`(appInfoMock.versionName).thenReturn(ideName)
        `when`(appInfoMock.fullVersion).thenReturn(fullVersion)

        val result = underTest.processRequest(request)

        assertThat(result).isInstanceOf(Success::class.java)
        assertThat((result as Success).body).isEqualTo("{\"ideName\":\"Version name\",\"description\":\"Full version (Community Edition) - " + project.name + "\"}")
    }

    @Test
    fun it_should_open_the_hotspot_for_well_formed_request() {
        val request = Request("$SHOW_HOTSPOT_ENDPOINT?project=p&hotspot=h&server=s", HttpMethod.GET, false)

        val result = underTest.processRequest(request)
        dispatchAllInvocationEventsInIdeEventQueue()

        assertThat(result).isInstanceOf(Success::class.java)
        assertThat((result as Success).body).isNull()
        verify(showRequestHandler).open(eq("p"), eq("h"), eq("s"))
    }

    @Test
    fun it_should_answer_with_bad_request_if_project_is_missing() {
        val request = Request("$SHOW_HOTSPOT_ENDPOINT?hotspot=h&server=s", HttpMethod.GET, false)

        val result = underTest.processRequest(request)

        assertThat(result).isEqualTo(BadRequest("The 'project' parameter is not specified"))
    }

    @Test
    fun it_should_answer_with_bad_request_if_hotspot_is_missing() {
        val request = Request("$SHOW_HOTSPOT_ENDPOINT?project=p&server=s", HttpMethod.GET, false)

        val result = underTest.processRequest(request)

        assertThat(result).isEqualTo(BadRequest("The 'hotspot' parameter is not specified"))
    }

    @Test
    fun it_should_answer_with_bad_request_if_server_is_missing() {
        val request = Request("$SHOW_HOTSPOT_ENDPOINT?project=p&hotspot=h", HttpMethod.GET, false)

        val result = underTest.processRequest(request)

        assertThat(result).isEqualTo(BadRequest("The 'server' parameter is not specified"))
    }

    @Test
    fun it_should_answer_with_not_found_if_there_is_no_token_listener() {
        val request = Request(TOKEN_ENDPOINT, HttpMethod.POST, false) { "{\"token\": " }

        val result = underTest.processRequest(request)

        assertThat(result).isEqualTo(NotFound)
    }

    @Test
    fun it_should_answer_with_bad_request_if_body_is_not_valid_json() {
        underTest.tokenListener = NoOpTokenListener()
        val request = Request(TOKEN_ENDPOINT, HttpMethod.POST, false) { "{\"token\": " }

        val result = underTest.processRequest(request)

        assertThat(result).isEqualTo(BadRequest("The 'token' field is missing"))
    }

    @Test
    fun it_should_answer_with_bad_request_if_body_is_does_not_have_token_field() {
        underTest.tokenListener = NoOpTokenListener()
        val request = Request(TOKEN_ENDPOINT, HttpMethod.POST, false) { "{\"field\": \"TOKEN\"}" }

        val result = underTest.processRequest(request)

        assertThat(result).isEqualTo(BadRequest("The 'token' field is missing"))
    }

    @Test
    fun it_should_publish_on_message_bus_when_receiving_token() {
        val request = Request(TOKEN_ENDPOINT, HttpMethod.POST, false) { "{\"token\": \"TOKEN\"}" }
        var receivedToken: String? = null
        underTest.tokenListener = object: UserTokenListener {
            override fun tokenReceived(userToken: String) {
                receivedToken = userToken
            }
        }

        val result = underTest.processRequest(request)
        dispatchAllInvocationEventsInIdeEventQueue()

        assertThat(result).isEqualTo(Success())
        assertThat(receivedToken).isEqualTo("TOKEN")
    }

}

class NoOpTokenListener : UserTokenListener {
    override fun tokenReceived(userToken: String) {
        // no-op
    }
}
