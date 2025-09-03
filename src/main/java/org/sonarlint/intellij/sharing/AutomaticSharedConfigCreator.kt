/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.sharing

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressRunner
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.jetbrains.rd.swing.textProperty
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.actions.OpenInBrowserAction
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.US_SONARCLOUD_URL
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.util.ProgressUtils.waitForFuture
import org.sonarlint.intellij.util.RegionUtils
import org.sonarlint.intellij.util.computeOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingMode
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion

class AutomaticSharedConfigCreator(
    private val projectKey: String,
    private val orgOrServerUrl: String,
    private val isSQ: Boolean,
    private val project: Project,
    private val overridesPerModule: Map<Module, String>,
    private val region: SonarCloudRegion?
) :
    DialogWrapper(false) {
    private var serverConnection: ServerConnection? = null
    private val centerPanel = JBPanel<JBPanel<*>>(GridBagLayout())
    private val createConnectionAction: DialogWrapperAction
    private val cancelConnectionAction: DialogWrapperAction
    private val warningIcon = JBLabel()
    private val redWarningIcon = JBLabel()
    private val warningLabel = SwingHelper.createHtmlViewer(false, null, null, JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    private val connectionNameField = JBTextField()
    private val tokenField = JBPasswordField()
    private val connectedModeDescriptionLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val connectedModeOverriddenBindingsLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val connectionLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val projectKeyLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val scURLLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val projectKeyField = JBTextField(projectKey).apply {
        isEditable = false
    }
    private val serverUrlField = JBTextField(orgOrServerUrl).apply {
        isEditable = false
    }
    private val connectionNameLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val tokenLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val tokenGenerationButton = JButton("Generate Token")

    init {
        title = if (isSQ) "Connect to This SonarQube Server Instance?" else "Connect to SonarQube Cloud?"
        val connectionNames = getGlobalSettings().serverNames
        connectionNameField.text = findFirstUniqueConnectionName(connectionNames, orgOrServerUrl)

        val connectionActionName = if (isSQ) "Connect to This SonarQube Server Instance" else "Connect to SonarQube Cloud"
        createConnectionAction = object : DialogWrapperAction(connectionActionName) {
            init {
                putValue(DEFAULT_ACTION, true)
                isEnabled = false
            }

            override fun doAction(e: ActionEvent) {
                if (handleConnectionCreation()) {
                    close(OK_EXIT_CODE)
                }
            }
        }

        cancelConnectionAction = object : DialogWrapperAction("Don't Connect") {
            init {
                putValue(DEFAULT_ACTION, false)
            }

            override fun doAction(e: ActionEvent) {
                doCancelAction()
            }
        }

        initPanel()

        if (isSQ) {
            tokenGenerationButton.addActionListener { openTokenCreationPage(orgOrServerUrl) }
        } else {
            tokenGenerationButton.addActionListener {
                openTokenCreationPage(RegionUtils.getUrlByRegion(region))
            }
        }
        isResizable = false

        init()
    }

    private fun handleConnectionCreation(): Boolean {
        val currBindingSuggestion = getSettingsFor(project).isBindingSuggestionsEnabled
        try {
            getSettingsFor(project).isBindingSuggestionsEnabled = false
            val serverConnectionBuilder = ServerConnection.newBuilder().setDisableNotifications(false).setToken(String(tokenField.password))
                .setName(connectionNameField.text)
            if (isSQ) {
                serverConnectionBuilder.setHostUrl(orgOrServerUrl)
            } else {
                serverConnectionBuilder.setOrganizationKey(orgOrServerUrl).setHostUrl(
                    RegionUtils.getUrlByRegion(region)
                )
                serverConnectionBuilder.setRegion(region?.name ?: SonarCloudRegion.EU.name)
            }
            serverConnection = serverConnectionBuilder.build()

            if (!validateConnection()) {
                return false
            }

            serverConnection.apply {
                val globalSettings = getGlobalSettings()
                getGlobalSettings().addServerConnection(this!!)
                val serverChangeListener =
                    ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC)
                // Notify in case the connection settings dialog is open to reflect the change
                serverChangeListener.changed(globalSettings.serverConnections)
            }

            val connection = getGlobalSettings().getServerConnectionByName(connectionNameField.text)
                .orElseThrow { IllegalStateException("Unable to find connection '${connectionNameField.text}'") }

            getService(project, ProjectBindingManager::class.java).bindTo(connection, projectKey, overridesPerModule,
                BindingMode.FROM_SUGGESTION, BindingSuggestionOrigin.SHARED_CONFIGURATION)
            val connectionTypeMessage = if (isSQ) "SonarQube Server instance" else "SonarQube Cloud organization"
            SonarLintProjectNotifications.get(project).simpleNotification(
                "Project successfully bound",
                "Local project bound to project '$projectKey' of $connectionTypeMessage '${connection.name}'. " +
                    "You can now enjoy all capabilities of SonarQube for IDE Connected Mode. The binding of this project can be updated in the SonarQube for IDE settings.",
                NotificationType.INFORMATION,
                OpenInBrowserAction("Learn more", null, SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK)
            )
        } finally {
            getSettingsFor(project).isBindingSuggestionsEnabled = currBindingSuggestion
        }
        return true
    }

    private fun initPanel() {
        warningIcon.setIconWithAlignment(AllIcons.General.InformationDialog, SwingConstants.TOP, SwingConstants.TOP)
        centerPanel.add(
            warningIcon,
            GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.NONE, JBUI.insets(0, 10), 0, 18)
        )

        val sqOrScMessage = if (isSQ) "SonarQube Server" else "SonarQube Cloud"
        connectedModeDescriptionLabel.text =
            "Connect SonarQube for IDE with $sqOrScMessage to apply the same code quality and security standards as your team, analyze more languages, " +
                "detect more issues, and receive notifications about the quality gate status."
        connectedModeDescriptionLabel.addHyperlinkListener(object : HyperlinkAdapter() {
            override fun hyperlinkActivated(e: HyperlinkEvent) {
                BrowserUtil.browse(e.url)
            }
        })

        // Avoid displaying too much information if there are many overrides
        if (overridesPerModule.size <= 5) {
            connectedModeOverriddenBindingsLabel.text = "The following modules will also be bound: " +
                overridesPerModule
                    .map { "'${it.key.name}' to '${it.value}'" }
                    .joinToString(", ")
        } else {
            connectedModeOverriddenBindingsLabel.text = "Some of your modules will also be automatically bound to different project keys (too many to display)."
        }

        connectionLabel.text = if (isSQ) "Server URL" else "SonarQube Cloud organization"
        projectKeyLabel.text = "Project key"
        scURLLabel.text = "SonarQube Cloud URL"
        val urlLabel = JBPanel<JBPanel<*>>(BorderLayout()).apply { add(serverUrlField, BorderLayout.CENTER) }
        val projectKeyFieldLabel = JBPanel<JBPanel<*>>(BorderLayout()).apply { add(projectKeyField, BorderLayout.CENTER) }

        var gridY = 0

        centerPanel.add(
            connectedModeDescriptionLabel,
            GridBagConstraints(1, gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0)
        )

        if (overridesPerModule.isNotEmpty()) {
            centerPanel.add(
                connectedModeOverriddenBindingsLabel,
                GridBagConstraints(1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0)
            )
        }

        centerPanel.add(
            projectKeyLabel,
            GridBagConstraints(1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
        )
        centerPanel.add(
            projectKeyFieldLabel,
            GridBagConstraints(1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
        )
        centerPanel.add(
            connectionLabel,
            GridBagConstraints(1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
        )
        centerPanel.add(
            urlLabel,
            GridBagConstraints(1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
        )

        if (isSQ) {
            redWarningIcon.icon = AllIcons.Ide.FatalError
            warningLabel.text = "Always ensure that your Server URL matches your SonarQube Server instance. " +
                "Letting SonarQube for IDE connect to an untrusted SonarQube Server instance is potentially dangerous."
            val warningPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(redWarningIcon, BorderLayout.WEST)
                add(warningLabel, BorderLayout.CENTER)
            }
            centerPanel.add(
                warningPanel,
                GridBagConstraints(
                    1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(30), 0, 0
                )
            )
        } else {
            if (getGlobalSettings().isRegionEnabled) {
                val scURLField = JBTextField(US_SONARCLOUD_URL).apply {
                    isEditable = false
                }

                centerPanel.add(
                    scURLLabel,
                    GridBagConstraints(1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
                )
                centerPanel.add(
                    scURLField,
                    GridBagConstraints(1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
                )
            }
        }

        connectionNameLabel.text = "Connection name"
        centerPanel.add(
            connectionNameLabel,
            GridBagConstraints(1, ++gridY,
                1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
        )

        centerPanel.add(
            connectionNameField,
            GridBagConstraints(1, ++gridY,
                1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
        )

        tokenLabel.text = "Token"
        centerPanel.add(
            tokenLabel,
            GridBagConstraints(1, ++gridY,
                1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0)
        )

        val listener: DocumentListener = object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                createConnectionAction.isEnabled = tokenField.textProperty().value.isNotEmpty()
            }
        }
        tokenField.document.addDocumentListener(listener)

        centerPanel.add(
            tokenField,
            GridBagConstraints(1, ++gridY,
                1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0)
        )

        centerPanel.add(
            tokenGenerationButton,
            GridBagConstraints(1, ++gridY,
                1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsBottom(20), 0, 0)
        )

        centerPanel.preferredSize = Dimension(600, 300)
    }

    fun chooseResolution(): ServerConnection? {
        showAndGet()
        return serverConnection
    }

    override fun createCenterPanel() = centerPanel

    override fun createActions() = arrayOf(cancelConnectionAction, createConnectionAction)

    override fun getPreferredFocusedComponent() = getButton(createConnectionAction)

    private fun findFirstUniqueConnectionName(connectionNames: Set<String>, newConnectionName: String): String {
        var suffix = 1
        var uniqueName = newConnectionName
        while (uniqueName in connectionNames) {
            uniqueName = "$newConnectionName-$suffix"
            suffix++
        }
        return uniqueName
    }

    private fun validateConnection(): Boolean {
        try {
            val progressWindow = ProgressWindow(true, false, null, centerPanel, "Cancel").apply {
                title = ("Validating connection\u2026")
            }
            val progressResult = ProgressRunner<ValidateConnectionResponse> { pi: ProgressIndicator ->
                computeOnPooledThread<ValidateConnectionResponse>("Validate Connection") {
                    val future = getService(BackendService::class.java).validateConnection(serverConnection!!)
                    waitForFuture(pi, future)
                }
            }
                .sync()
                .onThread(ProgressRunner.ThreadToUse.POOLED)
                .withProgress(progressWindow)
                .modal()
                .submitAndGet()
            progressResult.result?.let { res ->
                if (!res.isSuccess) {
                    SonarLintConsole.get(project).error("Connection test failed. Reason: " + res.message)
                    Messages.showErrorDialog(
                        centerPanel,
                        res.message,
                        "Connection Test Failed"
                    )
                    return false
                }
            }
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Connection test failed", e)
            Messages.showErrorDialog(
                centerPanel,
                "Failed to connect to the server. Please check the configuration.",
                "Connection Test Failed"
            )
            return false
        }
        return true
    }

    private fun openTokenCreationPage(serverUrl: String) {
        if (!BrowserUtil.isAbsoluteURL(serverUrl)) {
            Messages.showErrorDialog(centerPanel, "Cannot launch browser for URL: $serverUrl", "Invalid Server URL")
            return
        }
        val progressWindow = ProgressWindow(true, false, null, centerPanel, "Cancel").apply {
            title = ("Generating token\u2026")
        }

        try {
            val progressResult = ProgressRunner<HelpGenerateUserTokenResponse> { pi: ProgressIndicator ->
                computeOnPooledThread<HelpGenerateUserTokenResponse>("Generate User Token Task") {
                    val future = getService(BackendService::class.java).helpGenerateUserToken(serverUrl)
                    waitForFuture(pi, future)
                }
            }
                .sync()
                .onThread(ProgressRunner.ThreadToUse.POOLED)
                .withProgress(progressWindow)
                .modal()
                .submitAndGet()
            progressResult.result?.let { res ->
                res.token?.let {
                    tokenField.text = it
                }
            }
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Unable to Generate Token", e)
            Messages.showErrorDialog(centerPanel, e.message, "Unable to Generate Token")
        }
    }

}
