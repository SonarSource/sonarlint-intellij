/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

/**
 * The BulkFileListener is not tied to a specific project but global to the IDE instance
 */
class VirtualFileSystemListener(
    private val fileEventsNotifier: ModuleFileEventsNotifier = ModuleFileEventsNotifier(),
) : BulkFileListener {
    override fun before(events: List<VFileEvent>) {
        forwardEvents(events.filter { it is VFileMoveEvent || it is VFileDeleteEvent }) { ModuleFileEvent.Type.DELETED }
    }

    override fun after(events: List<VFileEvent>) {
        forwardEvents(events) {
            when (it) {
                is VFileDeleteEvent -> null
                is VFileMoveEvent -> ModuleFileEvent.Type.CREATED
                is VFileCopyEvent, is VFileCreateEvent -> ModuleFileEvent.Type.CREATED
                is VFileContentChangeEvent -> ModuleFileEvent.Type.MODIFIED
                is VFilePropertyChangeEvent -> null
                else -> {
                    GlobalLogOutput.get().log("Unknown file event type: $it", ClientLogOutput.Level.DEBUG)
                    null
                }
            }
        }
    }

    private fun forwardEvents(events: List<VFileEvent>, eventTypeConverter: (VFileEvent) -> ModuleFileEvent.Type?) {
        getService(VirtualFileSystemEventsHandler::class.java).forwardEventsAsync(events, eventTypeConverter, fileEventsNotifier)
    }
}
