/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.ui

import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.sun.management.HotSpotDiagnosticMXBean
import org.sonar.api.utils.ZipUtils
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.telemetry.LinkTelemetry
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Arrays
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent

data class HelpCard(
    val title: String,
    val linkText: String,
    val link: LinkTelemetry
)

private val DOCUMENTATION_CARD = HelpCard(
    "Want to know more about our product?",
    "Read the Documentation",
    LinkTelemetry.BASE_DOCS_HELP
)

private val COMMUNITY_CARD = HelpCard(
    "SonarQube for IDE support",
    "Get help in the Community Forum",
    LinkTelemetry.COMMUNITY_HELP
)

private val FEATURE_CARD = HelpCard(
    "Are you missing any feature?",
    "Go to Suggested Features",
    LinkTelemetry.SUGGEST_FEATURE_HELP
)

class SonarLintHelpAndFeedbackPanel(private val project: Project) : SimpleToolWindowPanel(false, false) {

    private val topLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val cardPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()
    private val flightRecorderPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()

    private lateinit var startButton: JButton
    private lateinit var stopButton: JButton
    private lateinit var threadDumpButton: JButton
    private lateinit var heapDumpButton: JButton

    private var currentRecordingFolder: Path? = null

    init {
        setupContent()
    }

    private fun setupContent() {
        val contentPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()
        contentPanel.layout = GridBagLayout()
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = GridBagConstraints.RELATIVE
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5, 30)
        }

        topLabel.apply {
            text = """
            Having issues with SonarQube for IDE? Open the <a href="#LogTab">Log tab</a>, 
            then <a href="#Verbose">enable the Verbose output</a>.<br>
            Share your verbose logs with us in a post on the Community Forum. We are happy to help you debug!
        """.trimIndent()

            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    when (e.description) {
                        "#LogTab" -> getService(project, SonarLintToolWindow::class.java).openLogTab()
                        "#Verbose" -> LinkTelemetry.TROUBLESHOOTING_PAGE.browseWithTelemetry()
                    }
                }
            }
            border = JBUI.Borders.emptyTop(25)
        }

        cardPanel.apply {
            layout = BoxLayout(cardPanel, BoxLayout.X_AXIS)
            add(generateHelpCard(DOCUMENTATION_CARD))
            add(Box.createHorizontalStrut(30))
            add(generateHelpCard(COMMUNITY_CARD))
            add(Box.createHorizontalStrut(30))
            add(generateHelpCard(FEATURE_CARD))
        }

        contentPanel.add(topLabel, constraints)

        constraints.gridy = 1
        contentPanel.add(JSeparator(SwingConstants.HORIZONTAL), constraints)

        constraints.gridy = 2
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.weighty = 0.0
        contentPanel.add(cardPanel, constraints)

        setupFlightRecorderPanel()

        constraints.gridy = 3
        contentPanel.add(JSeparator(SwingConstants.HORIZONTAL), constraints)

        constraints.gridy = 4
        constraints.fill = GridBagConstraints.BOTH
        constraints.weighty = 1.0
        contentPanel.add(flightRecorderPanel, constraints)

        val scrollPane = JBScrollPane(contentPanel).apply {
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
        }

        setContent(scrollPane)
    }

    private fun generateHelpCard(card: HelpCard): JBPanel<SonarLintHelpAndFeedbackPanel> {
        return JBPanel<SonarLintHelpAndFeedbackPanel>(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false)).apply {
            add(JBLabel(card.title).apply {
                font = JBFont.label().asBold()
            })
            add(ActionLink(card.linkText) { card.link.browseWithTelemetry() }.apply {
                setExternalLinkIcon()
            })
            maximumSize = Dimension(300, maximumSize.height)
        }
    }

    private fun setupFlightRecorderPanel() {
        flightRecorderPanel.apply {
            layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false)

            add(JBLabel("Flight Recorder").apply {
                font = JBFont.label().asBold()
                border = JBUI.Borders.emptyLeft(3)
            })

            add(SwingHelper.createHtmlViewer(false, null, null, null).apply {
                text = """
                Flight Recorder mode enables advanced diagnostics for troubleshooting SonarQube for IDE issues. When enabled, it collects detailed execution traces, logs, and system metrics.<br>
                Stopping the recording will create a folder on disk with diagnostic details. Please share an archive of this folder on our Community Forum when reporting a problem.
            """.trimIndent()
            })

            val buttonPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()
            buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

            startButton = JButton("Start Flight Recorder").apply {
                addActionListener {
                    startRecording()
                    updateButtonStates()
                }
            }

            stopButton = JButton("Stop Flight Recorder").apply {
                addActionListener {
                    runLongAction("Stopping Flight Recorder...") {
                        stopRecording()
                    }
                }
            }

            threadDumpButton = JButton("Capture Thread Dumps").apply {
                addActionListener {
                    runLongAction("Capturing Thread Dumps...") {
                        captureThreadDumps()
                    }
                }
            }

            heapDumpButton = JButton("Capture Heap Dumps").apply {
                addActionListener {
                    runLongAction("Capturing Heap Dumps...") {
                        captureHeapDumps()
                    }
                }
            }

            buttonPanel.add(startButton)
            buttonPanel.add(Box.createHorizontalStrut(10))
            buttonPanel.add(stopButton)
            buttonPanel.add(Box.createHorizontalStrut(10))
            buttonPanel.add(threadDumpButton)
            buttonPanel.add(Box.createHorizontalStrut(10))
            buttonPanel.add(heapDumpButton)

            add(buttonPanel)

            updateButtonStates()
        }
    }

    private fun runLongAction(title: String, runnable: Runnable) {
        disableAllButtons()
        ProgressManager.getInstance().run(object : Backgroundable(project, title, false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = true
                    runnable.run()
                } finally {
                    invokeLater { updateButtonStates() }
                }
            }
        })
    }

    private fun stopRecording() {
        captureLogs()
        currentRecordingFolder?.let {
            archive(it)
            RevealFileAction.openDirectory(it.parent)
            currentRecordingFolder = null
        }
    }

    private fun archive(folder: Path) {
        ZipUtils.zipDir(folder.toFile(), recordingsRootPath.resolve("${folder.fileName}.zip").toFile())
    }

    private fun captureLogs() {
        // ideally logs shouldn't come from the console, as it can be cleared and has limited size
        // they should come from the backend. Depends on SLCORE-1865
        writeFile("logs-", SonarLintConsole.get(project).content)
    }

    private fun startRecording() {
        currentRecordingFolder = recordingsRootPath.resolve("recording-" + timestamp())
    }

    private fun captureThreadDumps() {
        try {
            captureIdeThreadDump()
            captureBackendThreadDump()
            notify("Thread dumps captured successfully.")
        } catch (e: Exception) {
            handleError(e, "Unable to capture thread dumps")
        }
    }

    private fun handleError(exception: Exception, message: String) {
        SonarLintConsole.get(project).error(message, exception)
        SonarLintProjectNotifications.projectLessNotification(
            null,
            "$message, see logs for more details.",
            NotificationType.ERROR
        )
    }

    private fun captureBackendThreadDump() {
        // this should point to the JBR, that comes with jstack installed
        val jstackPath = System.getProperty("java.home")?.let { Paths.get(it).resolve("bin").resolve("jstack") }
        if (jstackPath == null) {
            SonarLintConsole.get(project).info("Unable to capture thread dumps, jstack not found")
            return
        }
        val folderPath = currentRecordingFolder ?: return
        val pid = getService(BackendService::class.java).getPid()
        if (pid == null) {
            SonarLintConsole.get(project).info("Unable to capture thread dumps, backend is not alive")
            return
        }
        val pb = ProcessBuilder(jstackPath.toAbsolutePath().toString(), pid.toString())
        val outputFile = folderPath.resolve("slcore-thread-dump-" + timestamp()).toFile()
        pb.redirectOutput(outputFile)
        pb.redirectErrorStream(true)
        val process = pb.start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            SonarLintConsole.get(project).info("Unable to capture thread dumps, jstack failed with exit code: $exitCode")
        }
    }

    private fun captureIdeThreadDump() {
        val threadDump = StringBuilder()
        val threadBean = ManagementFactory.getThreadMXBean()
        Arrays.stream(threadBean.dumpAllThreads(true, true))
            .forEach { t -> threadDump.append(t.toString()).append(System.lineSeparator()) }
        writeFile("ide-thread-dump-", threadDump.toString())
    }

    private fun captureHeapDumps() {
        try {
            captureIdeHeapDump()
            captureBackendHeapDump()
            notify("Heap dump captured successfully.")
        } catch (e: Exception) {
            handleError(e, "Unable to capture heap dump")
        }
    }

    private fun captureIdeHeapDump() {
        currentRecordingFolder?.let { folder ->
            val server = ManagementFactory.getPlatformMBeanServer()
            val bean = ManagementFactory.newPlatformMXBeanProxy(
                server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean::class.java
            )
            bean.dumpHeap(folder.resolve("ide-heap-dump-" + timestamp()).toAbsolutePath().toString() + ".hprof", true)
        }
    }

    private fun captureBackendHeapDump() {
        // this should point to the JBR, that comes with jcmd installed
        val jcmdPath = System.getProperty("java.home")?.let { Paths.get(it).resolve("bin").resolve("jcmd") }
        if (jcmdPath == null) {
            SonarLintConsole.get(project).info("Unable to capture thread dumps, jstack not found")
            return
        }
        val pid = getService(BackendService::class.java).getPid()
        if (pid == null) {
            SonarLintConsole.get(project).info("Unable to capture heap dump, backend is not alive")
            return
        }
        val folderPath = currentRecordingFolder ?: return
        val heapDumpFile = folderPath.resolve("backend-heap-dump-" + timestamp() + ".hprof")
        val pb =
            ProcessBuilder(jcmdPath.toAbsolutePath().toString(), pid.toString(), "GC.heap_dump", heapDumpFile.toAbsolutePath().toString())
        val process = pb.start()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            SonarLintConsole.get(project).info("Dump created at: ${heapDumpFile.toAbsolutePath()}")
        } else {
            SonarLintConsole.get(project).error("jcmd failed with exit code: $exitCode")
        }
    }

    private fun updateButtonStates() {
        val isFlightRecorderEnabled = currentRecordingFolder != null
        startButton.isEnabled = !isFlightRecorderEnabled
        stopButton.isEnabled = isFlightRecorderEnabled
        threadDumpButton.isEnabled = isFlightRecorderEnabled
        heapDumpButton.isEnabled = isFlightRecorderEnabled
    }

    private fun disableAllButtons() {
        startButton.isEnabled = false
        stopButton.isEnabled = false
        threadDumpButton.isEnabled = false
        heapDumpButton.isEnabled = false
    }

    private fun writeFile(fileNamePrefix: String, content: String) {
        currentRecordingFolder?.let { folder ->
            // ensure parent folder exists
            Files.createDirectories(folder)
            Files.writeString(folder.resolve(fileNamePrefix + timestamp()), content)
        }
    }

    private fun timestamp() = dateTimeFormatter.format(LocalDateTime.now())

    private fun notify(message: String) {
        SonarLintProjectNotifications.projectLessNotification(
            null,
            message,
            NotificationType.INFORMATION
        )
    }

    companion object {
        val recordingsRootPath: Path = Paths.get(PathManager.getTempPath()).resolve("sonarqube").resolve("flight_recorder")
        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-n")
    }

}
