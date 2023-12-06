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
import java.util.EnumSet
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.EmbeddedPlugins.extraEnabledLanguagesInConnectedMode
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarsource.sonarlint.core.commons.Language

@Service(Service.Level.PROJECT)
class PromotionProvider(private val project: Project) {

    private val languagesHavingAdvancedRules: Set<Language> = EnumSet.of(
        Language.JAVA, Language.PYTHON, Language.PHP, Language.JS, Language.TS, Language.CS
    )

    fun subscribeToTriggeringEvents() {
        val busConnection = project.messageBus.connect()
        with(busConnection) {
            subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    val extension = file.extension ?: return
                    val notifications = getSonarLintProjectNotifications(source)

                    if (!Settings.getSettingsFor(project).isBound && !Settings.getGlobalSettings().isPromotionDisabled) {
                        processExtraLanguagePromotion(notifications, extension)
                        processAdvancedLanguagePromotion(notifications, extension)
                    }
                }
            })
        }
    }

    private fun getSonarLintProjectNotifications(source: FileEditorManager): SonarLintProjectNotifications {
        return SonarLintUtils.getService(
            source.project,
            SonarLintProjectNotifications::class.java
        )
    }

    private fun processAdvancedLanguagePromotion(notifications: SonarLintProjectNotifications, extension: String) {
        val language = findLanguage(extension, languagesHavingAdvancedRules)

        if (language != null) {
            showPromotion(notifications, "Enable advanced " + language.label + " rules by connecting your project")
        }
    }

    private fun processExtraLanguagePromotion(notifications: SonarLintProjectNotifications, extension: String) {
        val language = findLanguage(extension, extraEnabledLanguagesInConnectedMode)

        if (language != null) {
            showPromotion(notifications, "Enable " + language.label + " analysis by connecting your project")
        }
    }

    private fun findLanguage(extension: String, languages: Set<Language>): Language? {
        return languages.find {
            it.defaultFileSuffixes.any { suffix ->
                suffix.equals(extension) || suffix.equals(".$extension")
            }
        }
    }

    private fun showPromotion(notifications: SonarLintProjectNotifications, content: String) {
        val lastModifiedDate: Instant? = getLastModifiedDate()

        if (shouldNotify(lastModifiedDate)) {
            notifications.notifyLanguagePromotion(content)
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

        return lastPromotionDate.isBefore(Instant.now().minus(Period.ofDays(PROMOTION_PERIOD)))
    }

    companion object {
        const val PROMOTION_PERIOD = 7
    }
}
