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
package org.sonarlint.intellij.fixtures

import com.intellij.testFramework.RunAll
import com.intellij.testFramework.runInEdtAndWait
import java.lang.reflect.Method
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext

internal class LightTestGuardsExtension : BeforeEachCallback, InvocationInterceptor, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        context.getStore(NAMESPACE).put(State::class.java, State())
    }

    override fun interceptBeforeEachMethod(
        invocation: InvocationInterceptor.Invocation<Void?>,
        invocationContext: ReflectiveInvocationContext<Method>,
        context: ExtensionContext,
    ) {
        invocation.proceed()
        val method = invocationContext.executable
        if (method.declaringClass == AbstractLightTests::class.java && method.name == "beforeEachLightTest") {
            // The app's registry and the fixture roots are available once the platform setup completes.
            val fixture = context.requiredTestInstance as AbstractLightTests
            val state = checkNotNull(context.getStore(NAMESPACE).get(State::class.java, State::class.java))
            state.disposables = DisposableLeakTracker(*fixture.disposerRoots())
            state.slowOperations = SlowOperationGuard()
        }
    }

    override fun afterEach(context: ExtensionContext) {
        val state = context.getStore(NAMESPACE).remove(State::class.java, State::class.java) ?: return
        RunAll.runAll(
            {
                state.slowOperations?.let { guard ->
                    runInEdtAndWait {
                        guard.use { pumpEdt() }
                    }
                }
            },
            { state.slowOperations?.assertNoViolations() },
            { state.disposables?.assertNoLeaks() },
            { state.threads.assertNoLeaks() },
        )
    }

    private class State {
        val threads = PluginThreadLeakTracker()
        var disposables: DisposableLeakTracker? = null
        var slowOperations: SlowOperationGuard? = null
    }

    private companion object {
        val NAMESPACE = ExtensionContext.Namespace.create(LightTestGuardsExtension::class.java)
    }
}
