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
package org.sonarlint.intellij.promotion

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.time.Instant
import java.time.Period
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.EmbeddedPlugins.extraEnabledLanguagesInConnectedMode
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications

@Service(Service.Level.PROJECT)
class PromotionProvider(private val project: Project) {

    fun subscribeToTriggeringEvents() {
        val busConnection = project.messageBus.connect()
        with(busConnection) {
            subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    if (!Settings.getSettingsFor(project).isBound && !Settings.getGlobalSettings().isPromotionDisabled) {
                        processPromotion(source, file)
                    }
                }
            })
        }
    }

    private fun processPromotion(source: FileEditorManager, file: VirtualFile) {
        val notifications: SonarLintProjectNotifications = SonarLintUtils.getService(
            source.project,
            SonarLintProjectNotifications::class.java
        )

        val extension = file.extension ?: return

        val language = findLanguage(extension)

        if (language != null) {
            showPromotion(notifications, language.label)
        }
    }

    private fun findLanguage(extension: String) = extraEnabledLanguagesInConnectedMode.find {
        it.defaultFileSuffixes.any { suffix ->
            suffix.equals(extension) || suffix.equals(".$extension")
        }
    }

    private fun showPromotion(notifications: SonarLintProjectNotifications, language: String) {
        val lastModifiedDate: Instant? = getLastModifiedDate()

        if (shouldNotify(lastModifiedDate)) {
            notifications.notifyWiderLanguageSupport(language)
            Settings.getGlobalSettings().lastPromotionNotificationDate = Instant.now().toEpochMilli()
        }
    }

    private fun getLastModifiedDate(): Instant? {
        val instantValue = Settings.getGlobalSettings().lastPromotionNotificationDate

        val lastModifiedDate: Instant? = if (instantValue != 0L) {
            Instant.ofEpochMilli(instantValue)
        } else {
            null
        }
        return lastModifiedDate
    }

    private fun shouldNotify(lastPromotionDate: Instant?): Boolean {
        val isDontAskAgain = Settings.getGlobalSettings().isPromotionDisabled

        if (isDontAskAgain) {
            return false
        }

        if (lastPromotionDate == null) {
            return true
        }

        return lastPromotionDate.isBefore(Instant.now().minus(Period.ofDays(PROMOTION_PERIOD)));
    }

    companion object {
        const val PROMOTION_PERIOD = 7
    }
}
