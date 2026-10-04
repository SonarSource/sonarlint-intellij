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
package org.sonarlint.intellij.fs

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.sonarlint.intellij.trigger.EventScheduler

@Service(Service.Level.APP)
class EditorFileChangeListener private constructor(private val scheduler: EventScheduler) : BulkAwareDocumentListener.Simple, Disposable {
    constructor() : this(EventScheduler("editor-changes", 1000, false))

    private val lock = Any()
    private var started = false
    private var disposed = false

    fun startListening() {
        synchronized(lock) {
            if (started || disposed) return
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, this)
            started = true
        }
    }

    override fun afterDocumentChange(document: Document) {
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        scheduler.notify(virtualFile)
    }

    override fun dispose() {
        synchronized(lock) {
            disposed = true
        }
        scheduler.stopScheduler()
    }

    companion object {
        internal fun createForTests(scheduler: EventScheduler) = EditorFileChangeListener(scheduler)
    }
}
