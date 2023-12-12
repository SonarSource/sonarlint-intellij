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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.util.EnumSet
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.EmbeddedPlugins.extraEnabledLanguagesInConnectedMode
import org.sonarlint.intellij.messages.AnalysisListener
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.trigger.TriggerType
import org.sonarsource.sonarlint.core.commons.Language

private const val LAST_PROMOTION_NOTIFICATION_DATE = "SonarLint.lastPromotionNotificationDate"
private const val FIRST_AUTO_ANALYSIS_DATE = "SonarLint.firstAutoAnalysisDate"
private const val WAS_REPORT_EVER_USED = "SonarLint.wasReportEverUsed"

@Service(Service.Level.PROJECT)
class PromotionProvider(private val project: Project) {

    private val languagesHavingAdvancedRules: Set<Language> = EnumSet.of(
        Language.JAVA, Language.PYTHON, Language.PHP, Language.JS, Language.TS, Language.CS
    )

    private val reportAnalysisTriggers: Set<TriggerType> = EnumSet.of(
        TriggerType.RIGHT_CLICK, TriggerType.ALL
    )

    private val autoAnalysisTriggers: Set<TriggerType> = EnumSet.of(
        TriggerType.EDITOR_OPEN, TriggerType.BINDING_UPDATE,
        TriggerType.SERVER_SENT_EVENT, TriggerType.CONFIG_CHANGE,
        TriggerType.EDITOR_CHANGE, TriggerType.COMPILATION
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
            subscribe<AnalysisListener>(AnalysisListener.TOPIC, object : AnalysisListener.Adapter() {
                override fun started(files: Collection<VirtualFile>, triggerType: TriggerType) {
                    val notifications = getSonarLintProjectNotifications()
                    val wasAutoAnalyzed = PropertiesComponent.getInstance().getLong(FIRST_AUTO_ANALYSIS_DATE, 0L) != 0L

                    if (!Settings.getSettingsFor(project).isBound && !Settings.getGlobalSettings().isPromotionDisabled && wasAutoAnalyzed) {
                        processFullProjectPromotion(notifications)
                    }

                    if (autoAnalysisTriggers.contains(triggerType) && !wasAutoAnalyzed) {
                        PropertiesComponent.getInstance().setValue(FIRST_AUTO_ANALYSIS_DATE, Instant.now().toEpochMilli().toString())
                    }

                    if (reportAnalysisTriggers.contains(triggerType)) {
                        PropertiesComponent.getInstance().setValue(WAS_REPORT_EVER_USED, true)
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

    private fun getSonarLintProjectNotifications(): SonarLintProjectNotifications {
        return SonarLintUtils.getService(
            project,
            SonarLintProjectNotifications::class.java
        )
    }

    private fun processFullProjectPromotion(notifications: SonarLintProjectNotifications) {
        val firstAutoAnalysis = Instant.ofEpochMilli(PropertiesComponent.getInstance().getLong(FIRST_AUTO_ANALYSIS_DATE, 0L))
        val wasReportEverUsed = PropertiesComponent.getInstance().getBoolean(WAS_REPORT_EVER_USED, false)

        if (!wasReportEverUsed && firstAutoAnalysis.isBefore(Instant.now().minus(FULL_PROJECT_PROMOTION_DURATION))) {
            showPromotion(notifications, "Detect issues in your whole project")
        }
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
            PropertiesComponent.getInstance().setValue(LAST_PROMOTION_NOTIFICATION_DATE, Instant.now().toEpochMilli().toString())
        }
    }

    private fun getLastModifiedDate(): Instant? {
        val instantValue = PropertiesComponent.getInstance().getLong(LAST_PROMOTION_NOTIFICATION_DATE, 0L)

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

        return lastPromotionDate.isBefore(Instant.now().minus(PROMOTION_PERIOD))
    }

    companion object {
        val PROMOTION_PERIOD: Period = Period.ofDays(7)
        val FULL_PROJECT_PROMOTION_DURATION: Duration = Duration.ofMinutes(20L)
    }
}
