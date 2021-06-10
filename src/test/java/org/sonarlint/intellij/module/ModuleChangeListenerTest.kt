package org.sonarlint.intellij.module

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.capture
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.eq
import org.sonarlint.intellij.messages.ProjectEngineListener
import org.sonarsource.sonarlint.core.client.api.common.ModuleInfo
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine

@RunWith(MockitoJUnitRunner::class)
class ModuleChangeListenerTest : AbstractSonarLintLightTests() {
    @Before
    fun prepare() {
        replaceProjectService(ProjectBindingManager::class.java, projectBindingManager)
        `when`(projectBindingManager.engineIfStarted).thenReturn(fakeEngine)
        moduleChangeListener = ModuleChangeListener(project)
        disposeOnTearDown(moduleChangeListener)
    }

    @Test
    fun should_declare_module_on_engine_when_module_added_to_project() {
        moduleChangeListener.moduleAdded(project, module)

        verify(fakeEngine).declareModule(capture(moduleInfoCaptor))
        val moduleInfo = moduleInfoCaptor.value
        assertThat(moduleInfo.key()).isEqualTo(module)
    }

    @Test
    fun should_stop_module_on_engine_when_module_removed_from_project() {
        moduleChangeListener.moduleRemoved(project, module)

        verify(fakeEngine).stopModule(eq(module))
    }

    @Test
    fun should_stop_modules_on_engine_when_project_is_closed() {
        ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectClosed(project)

        verify(fakeEngine).stopModule(eq(module))
    }

    @Test
    fun should_stop_and_recreate_modules_when_engine_changes() {
        moduleChangeListener.moduleAdded(project, module)
        project.messageBus.syncPublisher(ProjectEngineListener.TOPIC).engineChanged(fakeEngine, otherFakeEngine)

        verify(fakeEngine).stopModule(eq(module))
        verify(otherFakeEngine).declareModule(capture(moduleInfoCaptor))
        val moduleInfo = moduleInfoCaptor.value
        assertThat(moduleInfo.key()).isEqualTo(module)
    }

    private lateinit var moduleChangeListener: ModuleChangeListener

    @Mock
    private lateinit var projectBindingManager: ProjectBindingManager

    @Mock
    private lateinit var fakeEngine: SonarLintEngine

    @Mock
    private lateinit var otherFakeEngine: SonarLintEngine

    @Captor
    private lateinit var moduleInfoCaptor: ArgumentCaptor<ModuleInfo>
}
