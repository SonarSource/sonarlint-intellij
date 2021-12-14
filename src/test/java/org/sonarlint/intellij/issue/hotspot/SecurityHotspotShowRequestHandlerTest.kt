/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.issue.hotspot

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.any
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BoundProject
import org.sonarlint.intellij.core.ProjectBindingAssistant
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.eq
import org.sonarlint.intellij.issue.Location
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.serverapi.ServerApi
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot
import java.util.Optional

const val FILE_PATH = "com/sonarsource/sample/MyFile.java"
const val CONNECTED_URL = "serverUrl"
const val PROJECT_KEY = "projectKey"
const val HOTSPOT_KEY = "hotspotKey"

private fun aRemoteHotspot(textRange: ServerHotspot.TextRange): ServerHotspot {
  return ServerHotspot(
    "Very hotspot",
    FILE_PATH,
    textRange,
    "author",
    ServerHotspot.Status.TO_REVIEW,
    null,
    ServerHotspot.Rule("rulekey", "rulename", "category", ServerHotspot.Rule.Probability.HIGH, "", "", ""),
    ""
  )
}

@RunWith(MockitoJUnitRunner::class)
class SecurityHotspotShowRequestHandlerTest : AbstractSonarLintLightTests() {
  @Mock
  lateinit var projectBindingAssistant: ProjectBindingAssistant

  @Mock
  lateinit var toolWindow: SonarLintToolWindow

  @Mock
  lateinit var highlighter: EditorDecorator

  @Mock
  private lateinit var telemetry: SonarLintTelemetry

  private lateinit var requestHandler: SecurityHotspotShowRequestHandler

  @Before
  fun prepare() {
    requestHandler = SecurityHotspotShowRequestHandler(projectBindingAssistant, telemetry)
    replaceProjectService(SonarLintToolWindow::class.java, toolWindow)
    replaceProjectService(EditorDecorator::class.java, highlighter)
    clearNotifications()
  }

  @Test
  fun it_should_inform_telemetry_that_a_request_is_received() {
    requestHandler.open(PROJECT_KEY, HOTSPOT_KEY, CONNECTED_URL)

    verify(telemetry).showHotspotRequestReceived()
  }

  @Test
  fun it_should_do_nothing_when_there_is_no_bound_project() {
    `when`(projectBindingAssistant.bind(PROJECT_KEY, CONNECTED_URL)).thenReturn(null)

    requestHandler.open(PROJECT_KEY, HOTSPOT_KEY, CONNECTED_URL)

    verifyZeroInteractions(toolWindow)
  }

  @Test
  fun it_should_show_a_balloon_notification_when_an_error_occurs_when_fetching_hotspot_details() {
    val connection = aServerConnectionReturningHotspot(null)
    `when`(projectBindingAssistant.bind(PROJECT_KEY, CONNECTED_URL)).thenReturn(BoundProject(project, connection))

    requestHandler.open(PROJECT_KEY, HOTSPOT_KEY, CONNECTED_URL)

    assertThat(projectNotifications)
      .extracting("title", "content")
      .containsExactly(
        tuple(
          "Error opening security hotspot",
          "Cannot fetch hotspot details. Server is unreachable or credentials are invalid."
        )
      )
  }

  @Test
  fun it_should_partially_display_a_hotspot_and_a_balloon_notification_if_file_is_not_found() {
    val remoteHotspot = aRemoteHotspot(ServerHotspot.TextRange(1, 14, 1, 20))
    val connection = aServerConnectionReturningHotspot(remoteHotspot)
    `when`(projectBindingAssistant.bind(PROJECT_KEY, CONNECTED_URL)).thenReturn(BoundProject(project, connection))

    requestHandler.open(PROJECT_KEY, HOTSPOT_KEY, CONNECTED_URL)

    verify(toolWindow).show(eq(LocalHotspot(Location(null, null, "Very hotspot", "MyFile.java", null), remoteHotspot)))
    verifyZeroInteractions(highlighter)
    assertThat(projectNotifications)
      .extracting("title", "content")
      .containsExactly(tuple("Error opening security hotspot", "Cannot find hotspot file in the project."))
  }

  @Test
  fun it_should_open_a_hotspot_file_if_found() {
    val remoteHotspot = aRemoteHotspot(ServerHotspot.TextRange(1, 14, 1, 20))
    val connection = aServerConnectionReturningHotspot(remoteHotspot)
    `when`(projectBindingAssistant.bind(PROJECT_KEY, CONNECTED_URL)).thenReturn(BoundProject(project, connection))
    val file = myFixture.copyFileToProject(FILE_PATH)

    requestHandler.open(PROJECT_KEY, HOTSPOT_KEY, CONNECTED_URL)

    val localHotspotCaptor = ArgumentCaptor.forClass(LocalHotspot::class.java)
    verify(toolWindow).show(localHotspotCaptor.capture())
    val localHotspot = localHotspotCaptor.value
    assertThat(localHotspot.primaryLocation.file).isEqualTo(file)
    assertThat(localHotspot.primaryLocation.range)
      .extracting("startOffset", "endOffset")
      .containsOnly(14, 20)
    verify(highlighter).highlight(localHotspot)
    assertThat(FileEditorManager.getInstance(project).openFiles)
      .extracting<String, RuntimeException> { obj: VirtualFile -> obj.name }
      .containsOnly("MyFile.java")
  }

  @Test
  fun it_should_show_a_balloon_notification_when_the_text_range_does_not_match() {
    val remoteHotspot = aRemoteHotspot(ServerHotspot.TextRange(10, 14, 10, 20))
    val connection = aServerConnectionReturningHotspot(remoteHotspot)
    `when`(projectBindingAssistant.bind(PROJECT_KEY, CONNECTED_URL)).thenReturn(BoundProject(project, connection))
    val file = myFixture.copyFileToProject(FILE_PATH)

    requestHandler.open(PROJECT_KEY, HOTSPOT_KEY, CONNECTED_URL)

    val localHotspotCaptor = ArgumentCaptor.forClass(LocalHotspot::class.java)
    verify(toolWindow).show(localHotspotCaptor.capture())
    val (primaryLocation) = localHotspotCaptor.value
    assertThat(primaryLocation.file).isEqualTo(file)
    assertThat(primaryLocation.range).isNull()
    assertThat(projectNotifications)
      .extracting("title", "content")
      .containsExactly(
        tuple(
          "Error opening security hotspot",
          "The local source code does not match the branch/revision analyzed by SonarQube"
        )
      )
  }

  private fun aServerConnectionReturningHotspot(serverHotspot: ServerHotspot?): ServerConnection {
    val serverConnection = mock(ServerConnection::class.java)
    `when`(serverConnection.hostUrl).thenReturn(CONNECTED_URL)
    val serverApi = mock(ServerApi::class.java)
    `when`(serverConnection.api()).thenReturn(serverApi)
    val hotspotApi = mock(HotspotApi::class.java)
    `when`(serverApi.hotspot()).thenReturn(hotspotApi)
    `when`(hotspotApi.fetch(any())).thenReturn(Optional.ofNullable(serverHotspot))
    return serverConnection
  }
}
