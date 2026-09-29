/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.config.project

import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import java.awt.event.ActionEvent
import java.time.Duration
import java.util.concurrent.CompletableFuture
import javax.swing.JPanel
import javax.swing.Timer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.FuzzySearchProjectsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto

class SearchProjectKeyDialogTests : AbstractSonarLintLightTests() {

    private val backendService: BackendService = mock()
    private val connection = ServerConnection.newBuilder().setName("connection").setHostUrl("url").build()
    private var dialog: SearchProjectKeyDialog? = null

    @BeforeEach
    fun setup() {
        replaceApplicationService(BackendService::class.java, backendService)
    }

    @AfterEach
    fun disposeDialog() {
        dialog?.dispose()
    }

    @Test
    fun `debounces and trims search while preserving Core result order and ignoring stale results`() {
        val firstFuture = CompletableFuture<FuzzySearchProjectsResponse>()
        val secondFuture = CompletableFuture<FuzzySearchProjectsResponse>()
        whenever(backendService.fuzzySearchProjects(connection, "first")).thenReturn(firstFuture)
        whenever(backendService.fuzzySearchProjects(connection, "second")).thenReturn(secondFuture)
        val dialog = createDialog(emptyMap())
        val searchField = field<SearchTextField>(dialog, "searchTextField")
        val projectList = field<JBList<SonarProjectDto>>(dialog, "projectList")

        searchField.text = "ignored"
        searchField.text = "  first  "
        assertThat(projectList.emptyText.text).isEqualTo("Searching projects...")
        assertThat(field<Timer>(dialog, "searchTimer").initialDelay).isEqualTo(300)
        firePendingSearch(dialog)
        verify(backendService, never()).fuzzySearchProjects(connection, "ignored")
        verify(backendService).fuzzySearchProjects(connection, "first")
        searchField.text = " second "
        firePendingSearch(dialog)
        verify(backendService).fuzzySearchProjects(connection, "second")

        secondFuture.complete(response(project("second-b", "B"), project("second-a", "A")))
        await().untilAsserted {
            assertThat(projects(projectList).map { it.key })
                .containsExactly("second-b", "second-a")
        }
        firstFuture.complete(response(project("first", "First")))
        await().during(Duration.ofMillis(300)).untilAsserted {
            assertThat(projects(projectList).map { it.key })
                .containsExactly("second-b", "second-a")
        }
    }

    @Test
    fun `restores the cached list for blank search and remembers selection independently of the model`() {
        val alpha = project("alpha", "Alpha")
        val beta = project("beta", "Beta")
        val dialog = createDialog(mapOf(alpha.key to alpha, beta.key to beta), "alpha")
        val searchField = field<SearchTextField>(dialog, "searchTextField")
        val projectList = field<JBList<SonarProjectDto>>(dialog, "projectList")
        assertThat(projectList.selectedValue.key).isEqualTo("alpha")

        projectList.selectedIndex = 1
        searchField.text = "pending"
        assertThat(projects(projectList)).isEmpty()
        searchField.text = "   "

        assertThat(projects(projectList).map { it.key })
            .containsExactly("alpha", "beta")
        assertThat(projectList.selectedValue.key).isEqualTo("beta")
    }

    @Test
    fun `shows empty and failure messages`() {
        val emptyResponse = CompletableFuture.completedFuture(response())
        val failedResponse = CompletableFuture.failedFuture<FuzzySearchProjectsResponse>(IllegalStateException("boom"))
        whenever(backendService.fuzzySearchProjects(connection, "empty")).thenReturn(emptyResponse)
        whenever(backendService.fuzzySearchProjects(connection, "failure")).thenReturn(failedResponse)
        val dialog = createDialog(emptyMap())
        val searchField = field<SearchTextField>(dialog, "searchTextField")
        val projectList = field<JBList<SonarProjectDto>>(dialog, "projectList")

        searchField.text = "empty"
        firePendingSearch(dialog)
        await().untilAsserted {
            assertThat(projectList.emptyText.text).isEqualTo("No projects found")
        }
        searchField.text = "failure"
        firePendingSearch(dialog)
        await().untilAsserted {
            assertThat(projectList.emptyText.text)
                .isEqualTo("Could not search projects. Check the connection and try again.")
        }
    }

    @Test
    fun `does not issue a pending search after disposal`() {
        val dialog = createDialog(emptyMap())
        field<SearchTextField>(dialog, "searchTextField").text = "project"

        dialog.dispose()
        this.dialog = null
        firePendingSearch(dialog)

        verify(backendService, never()).fuzzySearchProjects(connection, "project")
    }

    @Test
    fun `does not apply results after disposal`() {
        val resultFuture = CompletableFuture<FuzzySearchProjectsResponse>()
        whenever(backendService.fuzzySearchProjects(connection, "project")).thenReturn(resultFuture)
        val dialog = createDialog(emptyMap())
        val searchField = field<SearchTextField>(dialog, "searchTextField")
        val projectList = field<JBList<SonarProjectDto>>(dialog, "projectList")

        searchField.text = "project"
        firePendingSearch(dialog)
        verify(backendService).fuzzySearchProjects(connection, "project")
        dialog.dispose()
        this.dialog = null
        resultFuture.complete(response(project("project", "Project")))

        await().during(Duration.ofMillis(300)).untilAsserted { assertThat(projects(projectList)).isEmpty() }
    }

    private fun firePendingSearch(dialog: SearchProjectKeyDialog) {
        val timer = field<Timer>(dialog, "searchTimer")
        timer.stop()
        timer.actionListeners.single().actionPerformed(ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "test"))
    }

    private fun createDialog(
        projectsByKey: Map<String, SonarProjectDto>,
        lastSelectedProjectKey: String? = null,
    ): SearchProjectKeyDialog {
        return SearchProjectKeyDialog(JPanel(), lastSelectedProjectKey, projectsByKey, connection).also { dialog = it }
    }

    private fun response(vararg projects: SonarProjectDto): FuzzySearchProjectsResponse {
        return mock<FuzzySearchProjectsResponse>().also {
            whenever(it.topResults).thenReturn(projects.toList())
        }
    }

    private fun project(key: String, name: String) = SonarProjectDto(key, name)

    private fun projects(projectList: JBList<SonarProjectDto>): List<SonarProjectDto> {
        return (0 until projectList.model.size).map(projectList.model::getElementAt)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(dialog: SearchProjectKeyDialog, name: String): T {
        return SearchProjectKeyDialog::class.java.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(dialog) as T
        }
    }
}
