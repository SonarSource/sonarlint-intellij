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
package org.sonarlint.intellij.mediumtests

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.modules
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManagerGate
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.assertj.core.api.Assumptions
import org.awaitility.Awaitility
import org.jetbrains.annotations.NotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.analysis.RunningAnalysesTracker
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarlint.intellij.trigger.TriggerType
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class StandaloneModeMediumTests : AbstractSonarLintLightTests() {
    private val diamondQuickFix = "SonarQube: Replace with <>"

    @BeforeEach
    fun notifyProjectOpened() {
        getService(project, RunningAnalysesTracker::class.java).finishAll()
        getService(BackendService::class.java).projectOpened(project)
        getService(BackendService::class.java).modulesAdded(project, listOf(module))
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, AnalysisReadinessCache::class.java).isReady).isTrue()
        }
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, RunningAnalysesTracker::class.java).isAnalysisRunning()).isFalse()
        }
    }

    @AfterEach
    fun notifyProjectClosed() {
        getService(BackendService::class.java).projectClosed(project)
        getService(BackendService::class.java).moduleRemoved(module)
    }

    @Test
    fun should_analyze_xml_file() {
        val fileToAnalyze = sendFileToBackend("src/file.xml")

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { it.getType() },
                { it.getCleanCodeAttribute() },
                { it.userSeverity },
                { it.getHighestImpact() },
                { it.getHighestQuality() },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } }
            )
            .containsExactly(
                tuple(
                    "xml:S1778",
                    "Remove all characters located before \"<?xml\".",
                    null,
                    org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute.COMPLETE,
                    null,
                    org.sonarsource.sonarlint.core.client.utils.ImpactSeverity.HIGH,
                    org.sonarsource.sonarlint.core.client.utils.SoftwareQuality.RELIABILITY,
                    Pair(62, 67),

                    )
            )
    }

    @Test
    @Disabled("Skipping temp:///src/src/style.css as it has not 'file' scheme")
    fun should_analyze_css_file() {
        val fileToAnalyze = sendFileToBackend("src/style.css")

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("css:S4647", "Unexpected invalid hex color \"#3c\"", Pair(4, 80)),
                tuple("css:S4647", "Unexpected invalid hex color \"#3cb371a\"", Pair(89, 171))
            )
    }

    @Test
    fun should_analyze_java_file() {
        val fileToAnalyze = sendFileToBackend("src/Main.java")

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("java:S1220", "Move this file to a named package.", null),
                tuple("java:S106", "Replace this use of System.out by a logger.", Pair(67, 77))
            )
    }

    @Test
    @Disabled("Provider \"temp\" not installed")
    fun should_analyze_js_in_yaml_file() {
        val fileToAnalyze = sendFileToBackend("src/lambda.yaml")

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactly(
                tuple("javascript:S1481", "Remove the declaration of the unused 'x' variable.", Pair(219, 220))
            )
    }

    @Test
    fun should_analyze_dockerfiles() {
        val fileToAnalyze = sendFileToBackend("src/Dockerfile")

        val (issues, highlightInfos) = analyzeAndHighlight(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactly(
                tuple("docker:S6476", "Replace \"from\" with upper case format \"FROM\".", Pair(0, 4))
            )

        assertThat(highlightInfos).hasSize(1)
    }

    @Test
    fun should_analyze_cloudformation_files() {
        val fileToAnalyze = sendFileToBackend("src/CloudFormation.yaml")

        val (issues, highlightInfos) = analyzeAndHighlight(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactly(
                tuple("cloudformation:S6295", "Make sure missing \"RetentionInDays\" property is intended here.", Pair(79, 98))
            )
        assertThat(highlightInfos).hasSize(1)
    }

    @Test
    fun should_analyze_terraform_files() {
        val fileToAnalyze = sendFileToBackend("src/Terraform.tf")

        val (issues, highlightInfos) = analyzeAndHighlight(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactly(
                tuple(
                    "terraform:S4423",
                    "Change this code to disable support of older TLS versions.",
                    Pair(87, 114)
                )
            )
        assertThat(highlightInfos).hasSize(1)
    }

    @Test
    fun should_analyze_kubernetes_files() {
        val fileToAnalyze = sendFileToBackend("src/Kubernetes.yaml")

        val (issues, highlightInfos) = analyzeAndHighlight(fileToAnalyze)

        // TODO Fix this - sometimes, for an unknown reason, the Kubernetes analyzer skips analysis
        Assumptions.assumeThat(issues).isNotEmpty()

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactly(
                tuple(
                    "kubernetes:S1135",
                    "Complete the task associated to this \"TODO\" comment.",
                    Pair(127, 144)
                )
            )
        // contains another annotation for the TO-DO
        assertThat(highlightInfos)
            .extracting({ it.description })
            .contains(tuple("Complete the task associated to this \"TODO\" comment."))
    }

    @Test
    @Disabled("Provider \"temp\" not installed")
    fun should_find_cross_file_python_issue() {
        val fileToAnalyze = myFixture.configureByFiles("src/main.py", "src/mod.py").first().virtualFile
        val module = project.modules[0]
        val listModuleFileEvent = listOf(
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                fileToAnalyze
            ),
        )

        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listModuleFileEvent), true
        )
        Awaitility.await().during(1, TimeUnit.SECONDS)

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("python:S930", "Add 1 missing arguments; 'add' expects 2 positional arguments.", Pair(45, 48))
            )
    }

    @Test
    @Disabled("Provider \"temp\" not installed")
    fun should_find_cross_file_python_issue_after_module_file_is_modified_in_editor() {
        // first one is opened in the current editor
        val files = myFixture.configureByFiles("src/mod.py", "src/main.py")
        val module = project.modules[0]
        val listModuleFileEvent = listOf(
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                files[0].virtualFile
            ),
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                files[1].virtualFile
            )
        )

        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listModuleFileEvent), true
        )
        Awaitility.await().during(1, TimeUnit.SECONDS)

        val moduleFile = files[0].virtualFile
        val fileToAnalyze = files[1].virtualFile
        // trigger a first analysis to build the table
        analyze(fileToAnalyze)

        myFixture.saveText(moduleFile, "def add(x): return x\n")
        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("python:S930", "Remove 1 unexpected arguments; 'add' expects 1 positional arguments.", Pair(21, 24))
            )
    }

    @Test
    @Disabled("Provider \"temp\" not installed")
    fun should_find_secrets_excluding_vcs_ignored_files() {
        sendFileToBackend("src/devenv.js")
        val fileToAnalyzeIgnored = sendFileToBackend("src/devenv_ignored.js")
        val fileToAnalyzeUnversionned = sendFileToBackend("src/devenv_unversionned.js")

        val myVcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
        val myVcs = MockAbstractVcs(project)
        try {
            myVcs.changeProvider = MyMockChangeProvider()
            myVcsManager.registerVcs(myVcs)
            myVcsManager.setDirectoryMapping("", myVcs.name)
            myVcsManager.waitForInitialized()

            val myChangeListManager = ChangeListManagerImpl.getInstanceImpl(project)
            val dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
            // Wait for the initial refresh status job to complete on the root directory
            myChangeListManager.waitEverythingDoneInTestMode()

            // Now force a specific status update for some files. It will ask the MyMockChangeProvider
            dirtyScopeManager.fileDirty(fileToAnalyzeIgnored)
            dirtyScopeManager.fileDirty(fileToAnalyzeUnversionned)
            myChangeListManager.waitEverythingDoneInTestMode()
            FileStatusManager.getInstance(project).fileStatusesChanged()

            // Ensure previous code worked as expected
            assertThat(myChangeListManager.isIgnoredFile(fileToAnalyzeIgnored)).isTrue
            assertThat(myChangeListManager.isUnversioned(fileToAnalyzeUnversionned)).isTrue

            val issues = analyzeAll()

            assertThat(issues)
                .extracting(
                    { it.file().name },
                    { it.getRuleKey() },
                    { it.message },
                    { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
                .containsExactlyInAnyOrder(
                    tuple(
                        "devenv.js",
                        "secrets:S6290",
                        "Make sure the access granted with this AWS access key ID is restricted",
                        Pair(286, 306)
                    ),
                    tuple(
                        "devenv_unversionned.js",
                        "secrets:S6290",
                        "Make sure the access granted with this AWS access key ID is restricted",
                        Pair(286, 306)
                    ),
                    tuple(
                        "devenv.js",
                        "javascript:S2703",
                        "Add the \"let\", \"const\" or \"var\" keyword to this declaration of \"s3Uploader\" to make it explicit.",
                        Pair(62, 72)
                    ),
                    tuple(
                        "devenv_unversionned.js",
                        "javascript:S2703",
                        "Add the \"let\", \"const\" or \"var\" keyword to this declaration of \"s3Uploader\" to make it explicit.",
                        Pair(62, 72)
                    ),
                )
        } finally {
            myVcsManager.unregisterVcs(myVcs)
        }
    }

    @Test
    fun should_apply_quick_fix_on_original_range_when_no_code_is_modified() {
        val virtualFile = sendFileToBackend("src/quick_fixes/single_quick_fix.input.java")

        analyze(virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        val availableIntention = myFixture.filterAvailableIntentions(diamondQuickFix)
        assertThat(availableIntention).isEmpty()
        myFixture.checkResultByFile("src/quick_fixes/single_quick_fix.expected.java")
    }

    @Test
    fun should_apply_quick_fix_on_adapted_range_when_code_is_modified_within_the_range() {
        val virtualFile = sendFileToBackend("src/quick_fixes/single_quick_fix.input.java")

        analyze(virtualFile)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.checkResultByFile("src/quick_fixes/single_quick_fix.expected.java")
    }

    @Test
    fun should_apply_multiple_quick_fixes_on_different_lines() {
        val virtualFile = sendFileToBackend("src/quick_fixes/multiple_quick_fixes_on_different_lines.input.java")

        analyze(virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.editor.caretModel.currentCaret.moveToOffset(140)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.checkResultByFile("src/quick_fixes/multiple_quick_fixes_on_different_lines.expected.java")
    }

    @Test
    @Disabled("Temp scheme")
    fun should_apply_overlapping_quick_fixes() {
        val expectedFile = myFixture.copyFileToProject("src/quick_fixes/overlapping_quick_fixes.expected.java")
        val file = myFixture.configureByFile("src/quick_fixes/overlapping_quick_fixes.input.java")

        analyze(file.virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention("SonarQube: Use \"Arrays.toString(array)\" instead"))
        myFixture.editor.caretModel.currentCaret.moveToOffset(180)
        myFixture.launchAction(myFixture.findSingleIntention("SonarQube: Merge this if statement with the enclosing one"))
        //Their stripTrailingSpaces function don't work
        myFixture.checkResult(expectedFile.getDocument()!!.text.trim(), true)
    }

    @Test
    fun should_apply_multiple_quick_fixes_on_same_line() {
        val virtualFile = sendFileToBackend("src/quick_fixes/multiple_quick_fixes_on_same_line.input.java")

        analyze(virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.editor.caretModel.currentCaret.moveToOffset(120)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.checkResultByFile("src/quick_fixes/multiple_quick_fixes_on_same_line.expected.java")
    }

    /**
     * A mock ChangeProvider that will compute file status based on file name
     */
    private class MyMockChangeProvider : ChangeProvider {
        override fun getChanges(
            @NotNull dirtyScope: VcsDirtyScope,
            @NotNull builder: ChangelistBuilder,
            @NotNull progress: ProgressIndicator,
            @NotNull addGate: ChangeListManagerGate,
        ) {
            for (path in dirtyScope.dirtyFiles) {
                if (path.name.contains("_ignored"))
                    builder.processIgnoredFile(path)
                else if (path.name.contains("_unversionned"))
                    builder.processUnversionedFile(path)
            }
        }

        override fun isModifiedDocumentTrackingRequired(): Boolean {
            return false
        }

    }

    private fun analyzeAndHighlight(vararg filesToAnalyze: VirtualFile): Pair<List<LiveIssue>, List<HighlightInfo>> {
        return analyze(*filesToAnalyze).toList() to myFixture.doHighlighting()
    }

    private fun analyze(vararg filesToAnalyze: VirtualFile): Collection<LiveIssue> {
        val submitter = getService(project, AnalysisSubmitter::class.java)
        val onTheFlyFindingsHolder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, AnalysisReadinessCache::class.java).isReady).isTrue()
        }

        submitter.autoAnalyzeFiles(filesToAnalyze.toList(), TriggerType.EDITOR_CHANGE)
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, RunningAnalysesTracker::class.java).isAnalysisRunning()).isFalse()
        }

        val issues = filesToAnalyze.toList().map {
            onTheFlyFindingsHolder.getIssuesForFile(it)
        }.toList().flatten()
        return issues
    }

    private fun analyzeAll(): List<LiveIssue> {
        val submitter = getService(project, AnalysisSubmitter::class.java)

        submitter.analyzeAllFiles()
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, RunningAnalysesTracker::class.java).isAnalysisRunning()).isFalse()
        }

        val onTheFlyFindingsHolder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        val issues = SonarLintAppUtils.visitAndAddAllFilesForProject(project).toList().map {
            onTheFlyFindingsHolder.getIssuesForFile(it)
        }.toList().flatten()
        return issues
    }

    private fun sendFileToBackend(filePath: String): VirtualFile {
        val file = myFixture.configureByFile(filePath)
        val module = project.modules[0]
        val listModuleFileEvent = listOf(
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                file.virtualFile
            )
        )

        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listModuleFileEvent), true
        )
        Awaitility.await().during(1, TimeUnit.SECONDS)

        return file.virtualFile
    }
}
