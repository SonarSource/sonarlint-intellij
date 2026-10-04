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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.swing.JComponent
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto

/** Credential UI workflows never wait on a worker. The owner and captured modality guard their continuation. */
object CredentialOperationRunner {
    @JvmStatic
    fun get(owner: Disposable, parent: JComponent, connection: ServerConnection, valid: BooleanSupplier,
            success: Consumer<Either<TokenDto, UsernamePasswordDto>>, failure: Consumer<Throwable>, finished: Runnable): Request =
        run(owner, parent, "Getting credentials from store…", valid, success, failure, finished) {
            getService(CredentialsService::class.java).getCredentials(connection, it)
        }

    @JvmStatic
    fun save(owner: Disposable, parent: JComponent, name: String, credentials: Either<TokenDto, UsernamePasswordDto>, valid: BooleanSupplier,
             success: Runnable, failure: Consumer<Throwable>, finished: Runnable): Request =
        run(owner, parent, "Saving credentials…", valid, Consumer<Unit> { success.run() }, failure, finished) {
            getService(CredentialsService::class.java).saveCredentials(name, credentials, it)
        }

    @JvmStatic
    fun erase(owner: Disposable, parent: JComponent, connection: ServerConnection, valid: BooleanSupplier,
              success: Runnable, failure: Consumer<Throwable>, finished: Runnable): Request =
        run(owner, parent, "Removing credentials…", valid, Consumer<Unit> { success.run() }, failure, finished) {
            getService(CredentialsService::class.java).eraseCredentials(connection, it)
        }

    private fun <T : Any> run(owner: Disposable, parent: JComponent, title: String, valid: BooleanSupplier,
                        success: Consumer<T>, failure: Consumer<Throwable>, finished: Runnable,
                        submit: (CredentialCancellation) -> CompletableFuture<T>): Request {
        val progress = ProgressWindow(true, false, null, parent, "Cancel")
        progress.title = title
        progress.isIndeterminate = true
        return start(owner, progress, valid, success, failure, finished, 30_000, submit)
    }

    internal fun <T : Any> start(owner: Disposable, progress: ProgressIndicator, valid: BooleanSupplier,
                          success: Consumer<T>, failure: Consumer<Throwable>, finished: Runnable, timeoutMillis: Long,
                          submit: (CredentialCancellation) -> CompletableFuture<T>): Request {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val request = Request(owner, progress, valid, ModalityState.current(), deadline)
        Disposer.register(owner, request)
        if (progress is Disposable) Disposer.register(request, progress)
        progress.start()
        request.start(success, failure, finished, submit)
        return request
    }

    class Request internal constructor(private val owner: Disposable, private val progress: ProgressIndicator,
                                       private val valid: BooleanSupplier, private val modality: ModalityState, deadline: Long) : Disposable {
        private val cancellation = CredentialCancellation(progress, deadline)
        private val terminal = AtomicBoolean()
        private val disposed = AtomicBoolean()
        private val progressStopped = AtomicBoolean()
        private var poll: ScheduledFuture<*>? = null

        internal fun <T : Any> start(success: Consumer<T>, failure: Consumer<Throwable>, finished: Runnable,
                               submit: (CredentialCancellation) -> CompletableFuture<T>) {
            poll = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
                cancellation.failure()?.let { complete(null, it, success, failure, finished) }
            }, 10, 10, TimeUnit.MILLISECONDS)
            try {
                submit(cancellation).whenComplete { result, error -> complete(result, error, success, failure, finished) }
            } catch (e: Exception) {
                complete(null, e, success, failure, finished)
            }
        }

        private fun <T : Any> complete(result: T?, error: Throwable?, success: Consumer<T>, failure: Consumer<Throwable>, finished: Runnable) {
            if (!terminal.compareAndSet(false, true)) return
            poll?.cancel(false)
            val cause = (if (error is CompletionException && error.cause != null) error.cause else error) ?: cancellation.failure()
            if (cause != null) cancellation.cancel()
            stopProgress()
            val schedulingError = cause ?: cancellation.failure()
            if (!disposed.get() && !Disposer.isDisposed(owner)) {
                ApplicationManager.getApplication().invokeLater({
                    try {
                        if (!disposed.get() && !Disposer.isDisposed(owner) && valid.asBoolean) {
                            finished.run()
                            val callbackError = schedulingError ?: cancellation.failure()
                            if (!disposed.get() && !Disposer.isDisposed(owner) && valid.asBoolean) {
                                when {
                                    callbackError == null -> success.accept(requireNotNull(result))
                                    callbackError !is CancellationException && callbackError !is ProcessCanceledException -> failure.accept(callbackError)
                                }
                            }
                        }
                    } finally {
                        Disposer.dispose(this)
                    }
                }, modality)
            }
        }

        override fun dispose() {
            disposed.set(true)
            terminal.set(true)
            poll?.cancel(false)
            cancellation.cancel()
            stopProgress()
        }

        private fun stopProgress() {
            if (progressStopped.compareAndSet(false, true)) progress.stop()
        }
    }
}
