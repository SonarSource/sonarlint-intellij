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
package org.sonarlint.intellij.promotion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.core.EnabledLanguages.extraEnabledLanguagesInConnectedMode
import org.sonarlint.intellij.messages.AnalysisListener
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.trigger.TriggerType
import org.sonarlint.intellij.ui.UiUtils
import org.sonarsource.sonarlint.core.commons.Language
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.util.EnumSet

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

    private val nonReportAnalysisTriggers: Set<TriggerType> = EnumSet.of(
        TriggerType.EDITOR_OPEN, TriggerType.BINDING_UPDATE,
        TriggerType.SERVER_SENT_EVENT, TriggerType.CONFIG_CHANGE,
        TriggerType.EDITOR_CHANGE, TriggerType.COMPILATION, TriggerType.CURRENT_FILE_ACTION
    )

    fun subscribeToTriggeringEvents() {
        val busConnection = project.messageBus.connect()
        with(busConnection) {
            subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    val extension = file.extension ?: return
                    val notifications = getSonarLintProjectNotifications(source)

                    if (isPromotionEnabled()) {
                        processExtraLanguagePromotion(notifications, extension)
                        processAdvancedLanguagePromotion(notifications, extension)
                    }
                }
            })

            subscribe<AnalysisListener>(AnalysisListener.TOPIC, object : AnalysisListener.Adapter() {
                override fun started(files: Collection<VirtualFile>, triggerType: TriggerType) {
                    val notifications = getSonarLintProjectNotifications()
                    val wasAutoAnalyzed = PropertiesComponent.getInstance().getLong(FIRST_AUTO_ANALYSIS_DATE, 0L) != 0L

                    if (isPromotionEnabled()) {
                        processNotificationsByType(triggerType, wasAutoAnalyzed, notifications, files)
                    }
                }
            })
        }
    }

    private fun processNotificationsByType(
        triggerType: TriggerType,
        wasAutoAnalyzed: Boolean,
        notifications: SonarLintProjectNotifications,
        files: Collection<VirtualFile>,
    ) {
        if (nonReportAnalysisTriggers.contains(triggerType)) {
            processAutoAnalysisTriggers(wasAutoAnalyzed, notifications)
        }

        if (reportAnalysisTriggers.contains(triggerType)) {
            processReportAnalysisTriggers(files, notifications)
        }

        if (triggerType == TriggerType.CHECK_IN) {
            processCICDProjectAnalysisPromotion(notifications)
        }
    }

    private fun processReportAnalysisTriggers(
        files: Collection<VirtualFile>,
        notifications: SonarLintProjectNotifications,
    ) {
        PropertiesComponent.getInstance().setValue(WAS_REPORT_EVER_USED, true)

        if (files.size > 1) {
            processFasterProjectAnalysisPromotion(notifications)
        }
    }

    private fun processAutoAnalysisTriggers(wasAutoAnalyzed: Boolean, notifications: SonarLintProjectNotifications) {
        if (wasAutoAnalyzed) {
            processFullProjectPromotion(notifications)
        } else {
            PropertiesComponent.getInstance().setValue(FIRST_AUTO_ANALYSIS_DATE, Instant.now().toEpochMilli().toString())
        }
    }

    private fun isPromotionEnabled() = getGlobalSettings().serverConnections.isEmpty() && !getSettingsFor(project).isBound && !getGlobalSettings().isPromotionDisabled

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
            UiUtils.runOnUiThread(project) {
                showPromotion(notifications, "Detect issues in your whole project")
            }
        }
    }

    private fun processFasterProjectAnalysisPromotion(notifications: SonarLintProjectNotifications) {
        UiUtils.runOnUiThread(project) {
            showPromotion(notifications, "Speed up the project-wide analysis")
        }
    }

    private fun processCICDProjectAnalysisPromotion(notifications: SonarLintProjectNotifications) {
        showPromotion(notifications, "Analyze your project in your CI/CD pipeline")
    }

    private fun processAdvancedLanguagePromotion(notifications: SonarLintProjectNotifications, extension: String) {
        val language = findLanguage(extension, languagesHavingAdvancedRules)

        if (language != null) {
            showPromotion(notifications, "Detect more security issues in your " + language.label + " files")
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
        val isDontAskAgain = getGlobalSettings().isPromotionDisabled

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
