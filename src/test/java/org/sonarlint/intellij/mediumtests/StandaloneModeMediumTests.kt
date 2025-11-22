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
package org.sonarlint.intellij.mediumtests

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.progress.ProgressIndicator
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
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.awaitility.Awaitility
import org.jetbrains.annotations.NotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisCallback
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.analysis.RunningAnalysesTracker
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class StandaloneModeMediumTests : AbstractSonarLintLightTests() {
    private val diamondQuickFix = "SonarQube: Replace with <>"

    @BeforeEach
    fun waitForCleanStart() {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, AnalysisReadinessCache::class.java).isModuleReady(module)).isTrue()
        }
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, RunningAnalysesTracker::class.java).isEmpty()).isTrue()
        }
    }

    @AfterEach
    fun gracefulFinish() {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, RunningAnalysesTracker::class.java).isEmpty()).isTrue()
        }
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
    @Disabled("Does not work")
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
    }

    @Test
    fun should_analyze_kubernetes_files() {
        val fileToAnalyze = sendFileToBackend("src/Kubernetes.yaml")

        val (issues, highlightInfos) = analyzeAndHighlight(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.getRuleKey() },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactly(
                tuple(
                    "kubernetes:S6897",
                    "Specify a storage request for this container.",
                    Pair(87, 91)
                ),
                tuple(
                    "kubernetes:S6892",
                    "Specify a CPU request for this container.",
                    Pair(87, 91)
                ),
                tuple(
                    "kubernetes:S6596",
                    "Use a specific version tag for the image.",
                    Pair(158, 163)
                ),
                tuple(
                    "kubernetes:S6873",
                    "Specify a memory request for this container.",
                    Pair(87, 91)
                ),
                tuple(
                    "kubernetes:S1135",
                    "Complete the task associated to this \"TODO\" comment.",
                    Pair(127, 144)
                )
            )
        // contains another annotation for the TO-DO
        assertThat(highlightInfos)
            .extracting({ it.description })
            .contains(tuple("TODO fix me"))
    }

    private fun readContent(filePath: String): String {
        val testDataPath = getTestDataPath()
        val sourceFile = java.nio.file.Path.of(testDataPath, filePath)
        return java.nio.file.Files.readString(sourceFile)
    }

    @Test
    @Disabled("Does not work")
    fun should_find_cross_file_python_issue() {
        val modContent = readContent("src/mod.py")
        val mainContent = readContent("src/main.py")

        val modFile = createTestPsiFile("src/mod.py", modContent).virtualFile
        val mainFile = createTestPsiFile("src/main.py", mainContent).virtualFile

        myFixture.configureFromExistingVirtualFile(mainFile)

        val module = module
        val listModuleFileEvent = listOf(
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                modFile
            ),
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                mainFile
            ),
        )

        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listModuleFileEvent), true
        )
        Awaitility.await().during(1, TimeUnit.SECONDS)

        val issues = analyze(mainFile)

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
    fun should_find_cross_file_python_issue_after_module_file_is_modified_in_editor() {
        val modContent = readContent("src/mod.py")
        val mainContent = readContent("src/main.py")

        val modFile = createTestPsiFile("src/mod.py", modContent).virtualFile
        val mainFile = createTestPsiFile("src/main.py", mainContent).virtualFile

        // first one is opened in the current editor
        myFixture.configureFromExistingVirtualFile(modFile)

        val module = module
        val listModuleFileEvent = listOf(
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                modFile
            ),
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                mainFile
            )
        )

        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listModuleFileEvent), true
        )
        Awaitility.await().during(1, TimeUnit.SECONDS)

        // trigger a first analysis to build the table
        analyze(mainFile)

        myFixture.saveText(modFile, "def add(x): return x\n")
        // Ensure change is saved to disk for backend to see
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments()

        // Notify backend about the modification
        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, modFile))),
            true
        )

        // Force analysis trigger via analyze()
        val issues = analyze(mainFile)

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
    @Disabled("Does not work")
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
    @Disabled("QuickFix tests do not work")
    fun should_apply_quick_fix_on_original_range_when_no_code_is_modified() {
        val virtualFile = sendFileToBackend("src/quick_fixes/single_quick_fix.input.java")

        analyze(virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        val availableIntention = myFixture.filterAvailableIntentions(diamondQuickFix)
        assertThat(availableIntention).isEmpty()
        myFixture.checkResultByFile("src/quick_fixes/single_quick_fix.expected.java")
    }

    @Test
    @Disabled("QuickFix tests do not work")
    fun should_apply_quick_fix_on_adapted_range_when_code_is_modified_within_the_range() {
        val virtualFile = sendFileToBackend("src/quick_fixes/single_quick_fix.input.java")

        analyze(virtualFile)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.checkResultByFile("src/quick_fixes/single_quick_fix.expected.java")
    }

    @Test
    @Disabled("QuickFix tests do not work")
    fun should_apply_multiple_quick_fixes_on_different_lines() {
        val virtualFile = sendFileToBackend("src/quick_fixes/multiple_quick_fixes_on_different_lines.input.java")

        analyze(virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.editor.caretModel.currentCaret.moveToOffset(140)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.checkResultByFile("src/quick_fixes/multiple_quick_fixes_on_different_lines.expected.java")
    }

    @Test
    @Disabled("QuickFix tests do not work")
    fun should_apply_overlapping_quick_fixes() {
        val expectedContent = readContent("src/quick_fixes/overlapping_quick_fixes.expected.java")
        // Use sendFileToBackend to create physical file and setup backend
        val virtualFile = sendFileToBackend("src/quick_fixes/overlapping_quick_fixes.input.java")

        analyze(virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention("SonarQube: Use \"Arrays.toString(array)\" instead"))
        myFixture.editor.caretModel.currentCaret.moveToOffset(180)
        myFixture.launchAction(myFixture.findSingleIntention("SonarQube: Merge this if statement with the enclosing one"))
        //Their stripTrailingSpaces function don't work
        myFixture.checkResult(expectedContent.trim(), true)
    }

    @Test
    @Disabled("QuickFix tests do not work")
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

    private fun analyzeAndHighlight(fileToAnalyze: VirtualFile): Pair<List<LiveIssue>, List<HighlightInfo>> {
        return analyze(fileToAnalyze).toList() to myFixture.doHighlighting()
    }

    private fun analyze(fileToAnalyze: VirtualFile): Collection<LiveIssue> {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, RunningAnalysesTracker::class.java).isEmpty()).isTrue()
        }

        val analysisResult = AtomicReference<AnalysisResult>()
        getService(project, AnalysisSubmitter::class.java).analyzeFilesWithCallback(setOf(fileToAnalyze), object : AnalysisCallback {
            override fun onSuccess(result: AnalysisResult) {
                analysisResult.set(result)
            }

            override fun onError(e: Throwable) {
                // Fail test on error
                throw RuntimeException(e)
            }
        })

        // Wait for analysis to finish and set the result
        Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(20, TimeUnit.SECONDS).untilAsserted {
            assertThat(analysisResult.get()).isNotNull
        }

        return analysisResult.get().findings.issuesPerFile[fileToAnalyze] ?: emptyList()
    }

    private fun findAllIssues(vararg filesToAnalyze: VirtualFile): List<LiveIssue> {
        val onTheFlyFindingsHolder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        return filesToAnalyze.toList().map {
            onTheFlyFindingsHolder.getIssuesForFile(it)
        }.toList().flatten()
    }

    private fun analyzeAll(): List<LiveIssue> {
        val submitter = getService(project, AnalysisSubmitter::class.java)

        submitter.analyzeAllFiles()
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, RunningAnalysesTracker::class.java).isEmpty()).isTrue()
        }

        val onTheFlyFindingsHolder = getService(project, AnalysisSubmitter::class.java).onTheFlyFindingsHolder
        val issues = SonarLintAppUtils.visitAndAddAllFilesForProject(project).toList().map {
            onTheFlyFindingsHolder.getIssuesForFile(it)
        }.toList().flatten()
        return issues
    }

    private fun sendFileToBackend(filePath: String): VirtualFile {
        val testDataPath = getTestDataPath()
        val sourceFile = java.nio.file.Path.of(testDataPath, filePath)
        val content = java.nio.file.Files.readString(sourceFile)

        val psiFile = createTestPsiFile(filePath, content)
        val virtualFile = psiFile.virtualFile

        myFixture.configureFromExistingVirtualFile(virtualFile)

        val module = module
        val listModuleFileEvent = listOf(
            VirtualFileEvent(
                ModuleFileEvent.Type.CREATED,
                virtualFile
            )
        )

        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listModuleFileEvent), true
        )

        // Safety before triggering an analysis: ensure backend processes the FS update
        Thread.sleep(500)

        return virtualFile
    }
}
