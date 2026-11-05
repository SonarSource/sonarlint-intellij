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
package org.sonarlint.intellij.config.global.wizard

import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.credentials.ControllablePasswordSafe
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.config.global.credentials.awaitRelease
import org.sonarlint.intellij.config.global.credentials.awaitUi

class ServerConnectionWizardTests : AbstractSonarLintLightTests() {
    @Test
    fun `normal Next saves once before committing and duplicate Next is ignored`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val safe = ControllablePasswordSafe().apply { onRead = { entered.countDown(); awaitRelease(release) } }
        replaceApplicationService(CredentialsService::class.java, CredentialsService { safe })
        val model = ConnectionWizardModel().setName("wizard").setServerType(ConnectionWizardModel.ServerType.SONARQUBE).setToken("test-value")
        val auth = RecordingAuthStep(model)
        val wizard = TestWizard(listOf(auth, NotificationsStep(model, false), ConfirmStep(false)))
        Disposer.register(testRootDisposable, wizard.disposable)
        wizard.next()
        wizard.next()
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(wizard.currentStep).isZero()
        assertThat(auth.commits).isZero()
        assertThat(auth.component.isEnabled).isTrue() // Container stays active so Cancel remains available.
        release.countDown()
        awaitUi { wizard.currentStep == 1 }
        assertThat(auth.commits).isEqualTo(1)
        assertThat(safe.writes).hasSize(1)
    }

    @Test
    fun `failed save leaves Authentication available for retry`() {
        val safe = ControllablePasswordSafe().apply { onRead = { throw IllegalStateException("store unavailable") } }
        replaceApplicationService(CredentialsService::class.java, CredentialsService { safe })
        val model = ConnectionWizardModel().setName("wizard-retry").setServerType(ConnectionWizardModel.ServerType.SONARQUBE).setToken("test-value")
        val auth = RecordingAuthStep(model)
        val wizard = TestWizard(listOf(auth, NotificationsStep(model, false), ConfirmStep(false)))
        Disposer.register(testRootDisposable, wizard.disposable)
        var errors = 0
        TestDialogManager.setTestDialog { errors++; TestDialog.OK.show(it) }
        try {
            wizard.next()
            awaitUi { errors == 1 }
            assertThat(wizard.currentStep).isZero()
            assertThat(auth.commits).isZero()
            safe.onRead = {}
            wizard.next()
            awaitUi { wizard.currentStep == 1 }
            assertThat(auth.commits).isEqualTo(1)
            assertThat(safe.writes).hasSize(1)
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
    }

    @Test
    fun `closing wizard while lookup runs prevents save and navigation`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val safe = ControllablePasswordSafe().apply { onRead = { entered.countDown(); awaitRelease(release) } }
        val service = CredentialsService { safe }
        replaceApplicationService(CredentialsService::class.java, service)
        val model = ConnectionWizardModel().setName("wizard-close").setServerType(ConnectionWizardModel.ServerType.SONARQUBE).setToken("test-value")
        val auth = RecordingAuthStep(model)
        val wizard = TestWizard(listOf(auth, NotificationsStep(model, false), ConfirmStep(false)))
        wizard.next()
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        wizard.doCancelAction()
        release.countDown()
        service.saveCredentials("wizard-close", org.sonarlint.intellij.config.global.credentials.token("replacement"),
            org.sonarlint.intellij.config.global.credentials.CredentialCancellation()).get(5, TimeUnit.SECONDS)
        assertThat(auth.commits).isZero()
        assertThat(safe.writes).hasSize(1)
        assertThat(safe.token("wizard-close")).isEqualTo("replacement")
    }

    private class TestWizard(steps: List<AbstractWizardStepEx>) : ServerConnectionWizard.ServerConnectionWizardEx(steps, "Test") {
        fun next() = doNextAction()
    }

    private class RecordingAuthStep(model: ConnectionWizardModel) : AuthStep(model) {
        var commits = 0
        override fun getPreferredFocusedComponent(): javax.swing.JComponent? = null
        override fun commit(commitType: CommitType) { if (commitType == CommitType.Next) commits++ }
    }
}
