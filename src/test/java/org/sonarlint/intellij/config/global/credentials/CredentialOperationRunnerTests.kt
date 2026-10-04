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
package org.sonarlint.intellij.config.global.credentials

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.messages.CredentialsChangeListener
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto

class CredentialOperationRunnerTests : AbstractSonarLintLightTests() {
    enum class Operation { READ, SAVE, ERASE }

    @Test
    fun `slow read leaves EDT responsive`() = slowProvider(Operation.READ)

    @Test
    fun `slow save leaves EDT responsive`() = slowProvider(Operation.SAVE)

    @Test
    fun `slow erase leaves EDT responsive`() = slowProvider(Operation.ERASE)

    private fun slowProvider(operation: Operation) {
        val safe = ControllablePasswordSafe()
        val service = CredentialsService { safe }
        safe.seedToken("connection", "old-value")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        if (operation == Operation.ERASE) safe.onWrite = { _, _ -> entered.countDown(); awaitRelease(release) }
        else safe.onRead = { entered.countDown(); awaitRelease(release) }
        var completed = false
        start(service, operation, success = { assertThat(ApplicationManager.getApplication().isDispatchThread).isTrue(); completed = true })
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            var heartbeat = false
            ApplicationManager.getApplication().invokeLater { heartbeat = true }
            UIUtil.dispatchAllInvocationEvents()
            assertThat(heartbeat).isTrue()
            assertThat(completed).isFalse()
            assertThat(safe.providerOnEdt).containsOnly(false)
        } finally {
            release.countDown()
        }
        awaitUi { completed }
    }

    @Test
    fun `cancelled read has no late success and restores controls quietly`() {
        val safe = ControllablePasswordSafe().apply { seedToken("connection", "old-value") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        safe.onRead = { entered.countDown(); awaitRelease(release) }
        val indicator = EmptyProgressIndicator()
        var restored = 0
        var successes = 0
        val service = CredentialsService { safe }
        start(service, Operation.READ, indicator, success = { successes++ }, finished = { restored++ })
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        indicator.cancel()
        awaitUi { restored == 1 }
        release.countDown()
        service.getCredentials(connection(), CredentialCancellation()).get(5, TimeUnit.SECONDS)
        UIUtil.dispatchAllInvocationEvents()
        assertThat(successes).isZero()
        assertThat(restored).isEqualTo(1)
    }

    @Test
    fun `disposed owner prevents late read callbacks`() {
        val safe = ControllablePasswordSafe().apply { seedToken("connection", "old-value") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        safe.onRead = { entered.countDown(); awaitRelease(release) }
        var callbacks = 0
        val service = CredentialsService { safe }
        val owner = Disposer.newDisposable()
        Disposer.register(testRootDisposable, owner)
        start(service, Operation.READ, success = { callbacks++ }, finished = { callbacks++ }, owner = owner)
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        Disposer.dispose(owner)
        release.countDown()
        service.getCredentials(connection(), CredentialCancellation()).get(5, TimeUnit.SECONDS)
        UIUtil.dispatchAllInvocationEvents()
        assertThat(callbacks).isZero()
    }

    @Test
    fun `invalidated callback cannot update a replacement UI`() {
        val safe = ControllablePasswordSafe().apply { seedToken("connection", "old-value") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        safe.onRead = { entered.countDown(); awaitRelease(release) }
        var valid = true
        var callbacks = 0
        val service = CredentialsService { safe }
        start(service, Operation.READ, valid = { valid }, success = { callbacks++ }, finished = { callbacks++ })
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        valid = false
        release.countDown()
        service.getCredentials(connection(), CredentialCancellation()).get(5, TimeUnit.SECONDS)
        UIUtil.dispatchAllInvocationEvents()
        assertThat(callbacks).isZero()
    }

    @Test
    fun `cancelling a save during its lookup prevents setter`() {
        val safe = ControllablePasswordSafe()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        safe.onRead = { entered.countDown(); awaitRelease(release) }
        val service = CredentialsService { safe }
        val cancellation = CredentialCancellation()
        val future = service.saveCredentials("connection", token("new-value"), cancellation)
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        cancellation.cancel()
        release.countDown()
        assertThat(runCatching { future.get(5, TimeUnit.SECONDS) }.isFailure).isTrue()
        assertThat(safe.writes).isEmpty()
    }

    @Test
    fun `indicator cancellation at lookup completion prevents setter without polling`() {
        val indicator = EmptyProgressIndicator()
        val safe = ControllablePasswordSafe().apply { onRead = { indicator.cancel() } }
        val future = CredentialsService { safe }.saveCredentials("cancel-at-read", token("new-value"), CredentialCancellation(indicator))
        assertThat(runCatching { future.get(5, TimeUnit.SECONDS) }.isFailure).isTrue()
        assertThat(safe.writes).isEmpty()
    }

    @Test
    fun `expired deadline prevents provider access without polling`() {
        val safe = ControllablePasswordSafe()
        val future = CredentialsService { safe }.saveCredentials("expired", token("new-value"),
            CredentialCancellation(deadlineNanos = System.nanoTime() - 1))
        assertThat(runCatching { future.get(5, TimeUnit.SECONDS) }.exceptionOrNull()?.cause).isInstanceOf(TimeoutException::class.java)
        assertThat(safe.providerOnEdt).isEmpty()
        assertThat(safe.writes).isEmpty()
    }

    @Test
    fun `indicator cancellation after completion suppresses scheduled success`() {
        val indicator = EmptyProgressIndicator()
        var restored = 0
        var successes = 0
        CredentialOperationRunner.start(testRootDisposable, indicator, BooleanSupplier { true }, Consumer<Unit> { successes++ },
            Consumer { throw AssertionError(it) }, Runnable { restored++ }, 30_000) {
            java.util.concurrent.CompletableFuture.completedFuture(Unit)
        }
        // Completion has already cancelled its poll; only the queued EDT continuation can observe this cancellation.
        indicator.cancel()
        UIUtil.dispatchAllInvocationEvents()
        assertThat(successes).isZero()
        assertThat(restored).isEqualTo(1)
    }

    @Test
    fun `expired deadline at provider completion suppresses success without polling`() {
        var restored = 0
        var successes = 0
        var error: Throwable? = null
        CredentialOperationRunner.start(testRootDisposable, EmptyProgressIndicator(), BooleanSupplier { true }, Consumer<Unit> { successes++ },
            Consumer { error = it }, Runnable { restored++ }, 0) {
            java.util.concurrent.CompletableFuture.completedFuture(Unit)
        }
        UIUtil.dispatchAllInvocationEvents()
        assertThat(successes).isZero()
        assertThat(restored).isEqualTo(1)
        assertThat(error).isInstanceOf(TimeoutException::class.java)
    }

    @Test
    fun `cancelled queued request never accesses provider`() {
        val queue = CredentialOperationQueue()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = queue.submit("connection", CredentialCancellation()) { entered.countDown(); awaitRelease(release) }
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        val cancellation = CredentialCancellation()
        var called = false
        val second = queue.submit("connection", cancellation) { called = true }
        cancellation.cancel()
        release.countDown()
        first.get(5, TimeUnit.SECONDS)
        assertThat(runCatching { second.get(5, TimeUnit.SECONDS) }.isFailure).isTrue()
        assertThat(called).isFalse()
    }

    @Test
    fun `timeout restores controls once and ignores eventual success`() {
        val safe = ControllablePasswordSafe().apply { seedToken("connection", "old-value") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        safe.onRead = { entered.countDown(); awaitRelease(release) }
        var restores = 0
        var successes = 0
        var failure: Throwable? = null
        val service = CredentialsService { safe }
        start(service, Operation.READ, timeoutMillis = 50, success = { successes++ },
            failure = { failure = it }, finished = { restores++ })
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        awaitUi { failure != null }
        assertThat(failure).isInstanceOf(TimeoutException::class.java)
        release.countDown()
        service.getCredentials(connection(), CredentialCancellation()).get(5, TimeUnit.SECONDS)
        UIUtil.dispatchAllInvocationEvents()
        assertThat(successes).isZero()
        assertThat(restores).isEqualTo(1)
    }

    @Test
    fun `provider failure restores controls once`() {
        val safe = ControllablePasswordSafe().apply { onRead = { throw IllegalStateException("store locked") } }
        var restores = 0
        var failures = 0
        start(CredentialsService { safe }, Operation.SAVE, failure = {
            assertThat(it).hasMessage("store locked")
            failures++
        }, finished = { restores++ })
        awaitUi { failures == 1 }
        assertThat(restores).isEqualTo(1)
        assertThat(safe.writes).isEmpty()
    }

    @Test
    fun `retry waits for cancelled noninterruptible setter and actual changes still publish events`() {
        val safe = ControllablePasswordSafe().apply { seedToken("connection", "old-value") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        safe.onWrite = { _, _ -> if (calls.incrementAndGet() == 1) { entered.countDown(); awaitRelease(release) } }
        val changes = CopyOnWriteArrayList<String>()
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(CredentialsChangeListener.TOPIC,
            CredentialsChangeListener { changes.add(it) })
        val service = CredentialsService { safe }
        val cancellation = CredentialCancellation()
        val first = service.saveCredentials("connection", token("first-value"), cancellation)
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        cancellation.cancel()
        val second = service.saveCredentials("connection", token("second-value"), CredentialCancellation())
        assertThat(second.isDone).isFalse()
        assertThat(calls).hasValue(1)
        // A different name is independent of the occupied lane.
        service.saveCredentials("other", token("other-value"), CredentialCancellation()).get(5, TimeUnit.SECONDS)
        release.countDown()
        first.get(5, TimeUnit.SECONDS)
        second.get(5, TimeUnit.SECONDS)
        assertThat(safe.token("connection")).isEqualTo("second-value")
        assertThat(changes).containsExactly("connection", "connection")
    }

    @Test
    fun `erase and replacement save share lane after cancellation`() {
        val safe = ControllablePasswordSafe().apply { seedToken("connection", "old-value") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        safe.onWrite = { _, _ -> if (calls.incrementAndGet() == 1) { entered.countDown(); awaitRelease(release) } }
        val service = CredentialsService { safe }
        val cancellation = CredentialCancellation()
        val erased = service.eraseCredentials(connection(), cancellation)
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        cancellation.cancel()
        val replacement = service.saveCredentials("connection", token("replacement-value"), CredentialCancellation())
        assertThat(replacement.isDone).isFalse()
        release.countDown()
        assertThat(runCatching { erased.get(5, TimeUnit.SECONDS) }.isFailure).isTrue()
        replacement.get(5, TimeUnit.SECONDS)
        assertThat(safe.writes.map { it.second?.getPasswordAsString() }).containsExactly(null, "replacement-value")
        assertThat(safe.token("connection")).isEqualTo("replacement-value")
    }

    private fun start(service: CredentialsService, operation: Operation, indicator: EmptyProgressIndicator = EmptyProgressIndicator(),
                      valid: () -> Boolean = { true }, timeoutMillis: Long = 30_000,
                      success: () -> Unit = {}, failure: (Throwable) -> Unit = { throw AssertionError(it) }, finished: () -> Unit = {},
                      owner: com.intellij.openapi.Disposable = testRootDisposable): CredentialOperationRunner.Request =
        CredentialOperationRunner.start(owner, indicator, BooleanSupplier(valid), Consumer<Any> { success() },
            Consumer(failure), Runnable(finished), timeoutMillis) { cancellation ->
            when (operation) {
                Operation.READ -> service.getCredentials(connection(), cancellation).thenApply { it as Any }
                Operation.SAVE -> service.saveCredentials("connection", token("new-value"), cancellation).thenApply { it as Any }
                Operation.ERASE -> service.eraseCredentials(connection(), cancellation).thenApply { it as Any }
            }
        }
}

internal fun connection(name: String = "connection"): ServerConnection = ServerConnection.newBuilder().setName(name).build()
internal fun token(value: String): Either<TokenDto, UsernamePasswordDto> = Either.forLeft(TokenDto(value))

internal fun awaitRelease(latch: CountDownLatch) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (latch.count > 0) {
        try {
            check(latch.await(maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) { "Provider was never released" }
        } catch (_: InterruptedException) {
            // Model an OS credential provider that does not obey worker interruption.
        }
    }
}

internal fun awaitUi(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!condition() && System.nanoTime() < deadline) {
        UIUtil.dispatchAllInvocationEvents()
        Thread.sleep(10)
    }
    assertThat(condition()).isTrue()
}

internal class ControllablePasswordSafe : PasswordSafe() {
    private val values = ConcurrentHashMap<String, Credentials>()
    val providerOnEdt = CopyOnWriteArrayList<Boolean>()
    val writes = CopyOnWriteArrayList<Pair<CredentialAttributes, Credentials?>>()
    var onRead: (CredentialAttributes) -> Unit = {}
    var onWrite: (CredentialAttributes, Credentials?) -> Unit = { _, _ -> }

    fun seedToken(name: String, value: String) { values[generateServiceName("SonarLint", "server:$name:token")] = Credentials(null, value) }
    fun token(name: String): String? = values[generateServiceName("SonarLint", "server:$name:token")]?.getPasswordAsString()
    override fun get(attributes: CredentialAttributes): Credentials? {
        providerOnEdt.add(ApplicationManager.getApplication().isDispatchThread)
        onRead(attributes)
        return values[attributes.serviceName]
    }
    override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
        providerOnEdt.add(ApplicationManager.getApplication().isDispatchThread)
        onWrite(attributes, credentials)
        writes.add(attributes to credentials)
        if (credentials == null) values.remove(attributes.serviceName) else values[attributes.serviceName] = credentials
    }
    override fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean) = set(attributes, credentials)
    override var isRememberPasswordByDefault = true
    override val isMemoryOnly = true
    override fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials) = true
    override fun getAsync(attributes: CredentialAttributes): Promise<Credentials?> = AsyncPromise<Credentials?>().apply { setResult(get(attributes)) }
}
