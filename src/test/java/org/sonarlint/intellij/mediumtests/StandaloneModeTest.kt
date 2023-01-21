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
package org.sonarlint.intellij.mediumtests

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.jetbrains.annotations.NotNull
import org.junit.Before
import org.junit.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.issue.IssueManager
import org.sonarlint.intellij.issue.LiveIssue
import org.sonarlint.intellij.util.ProjectUtils

class StandaloneModeTest : AbstractSonarLintLightTests() {

    private val diamondQuickFix = "SonarLint: Replace with <>"

    @Before
    fun prepare() {
        engineManager.stopAllEngines(false)
    }

    @Test
    fun should_analyze_xml_file() {
        val fileToAnalyze = myFixture.configureByFile("src/file.xml").virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactly(
                tuple("xml:S1778", "Remove all characters located before \"<?xml\".", Pair(62, 67))
            )
    }

    @Test
    fun should_analyze_css_file() {
        val fileToAnalyze = myFixture.configureByFile("src/style.css").virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("css:S4647", "Unexpected invalid hex color \"#3c\"", Pair(4, 80)),
                tuple("css:S4647", "Unexpected invalid hex color \"#3cb371a\"", Pair(89, 171))
            )
    }

    @Test
    fun should_analyze_java_file() {
        val fileToAnalyze = myFixture.configureByFile("src/Main.java").virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("java:S1220", "Move this file to a named package.", null),
                tuple("java:S106", "Replace this use of System.out or System.err by a logger.", Pair(67, 77))
            )
    }

    @Test
    fun should_analyze_js_in_yaml_file() {
        val fileToAnalyze = myFixture.configureByFile("src/lambda.yaml").virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactly(
                tuple("javascript:S1481", "Remove the declaration of the unused 'x' variable.", Pair(219, 220))
            )
    }

    @Test
    fun should_find_cross_file_python_issue() {
        val fileToAnalyze = myFixture.configureByFiles("src/main.py", "src/mod.py").first().virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("python:S930", "Add 1 missing arguments; 'add' expects 2 positional arguments.", Pair(45, 48))
            )
    }

    @Test
    fun should_find_cross_file_python_issue_after_module_file_is_modified_in_editor() {
        // first one is opened in the current editor
        val files = myFixture.configureByFiles("src/mod.py", "src/main.py")
        val moduleFile = files[0].virtualFile
        val fileToAnalyze = files[1].virtualFile
        // trigger a first analysis to build the table
        analyze(fileToAnalyze)

        myFixture.saveText(moduleFile, "def add(x): return x\n")
        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.message },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("python:S930", "Remove 1 unexpected arguments; 'add' expects 1 positional arguments.", Pair(21, 24))
            )
    }

    @Test
    fun should_find_secrets_excluding_vcs_ignored_files() {
        myFixture.configureByFile("src/devenv.js").virtualFile
        val fileToAnalyzeIgnored = myFixture.configureByFile("src/devenv_ignored.js").virtualFile
        val fileToAnalyzeUnversionned = myFixture.configureByFile("src/devenv_unversionned.js").virtualFile

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
                    { it.psiFile().name },
                    { it.ruleKey },
                    { it.message },
                    { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
                .containsExactlyInAnyOrder(
                    tuple("devenv.js", "secrets:S6290", "Make sure this AWS Access Key ID is not disclosed.", Pair(286, 306)),
                    tuple("devenv.js", "javascript:S2703", "Add the \"let\", \"const\" or \"var\" keyword to this declaration of \"s3Uploader\" to make it explicit.", Pair(62, 72)),
                    tuple("devenv_unversionned.js", "secrets:S6290", "Make sure this AWS Access Key ID is not disclosed.", Pair(286, 306)),
                    tuple("devenv_unversionned.js", "javascript:S2703", "Add the \"let\", \"const\" or \"var\" keyword to this declaration of \"s3Uploader\" to make it explicit.", Pair(62, 72))
                )
        } finally {
            myVcsManager.unregisterVcs(myVcs)
        }
    }

    @Test
    fun should_apply_quick_fix_on_original_range_when_no_code_is_modified() {
        val file = myFixture.configureByFile("src/quick_fixes/single_quick_fix.input.java")
        analyze(file.virtualFile)

        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))

        myFixture.checkResultByFile("src/quick_fixes/single_quick_fix.expected.java")
    }

    @Test
    fun should_apply_quick_fix_on_adapted_range_when_code_is_modified_within_the_range() {
        val file = myFixture.configureByFile("src/quick_fixes/single_quick_fix.input.java")
        analyze(file.virtualFile)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)

        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))

        myFixture.checkResultByFile("src/quick_fixes/single_quick_fix.expected.java")
    }

    @Test
    fun should_apply_multiple_quick_fixes_on_different_lines() {
        val file = myFixture.configureByFile("src/quick_fixes/multiple_quick_fixes_on_different_lines.input.java")
        analyze(file.virtualFile)

        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.editor.caretModel.currentCaret.moveToOffset(140)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))

        myFixture.checkResultByFile("src/quick_fixes/multiple_quick_fixes_on_different_lines.expected.java")
    }

    @Test
    fun should_apply_overlapping_quick_fixes_on_same_line() {
        val file = myFixture.configureByFile("src/quick_fixes/overlapping_quick_fixes.input.java")
        analyze(file.virtualFile)

        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.editor.caretModel.currentCaret.moveToOffset(58)
        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove this unused private field"))

        myFixture.checkResultByFile("src/quick_fixes/overlapping_quick_fixes.expected.java")
    }

    @Test
    fun should_apply_multiple_quick_fixes_on_same_line() {
        val file = myFixture.configureByFile("src/quick_fixes/multiple_quick_fixes_on_same_line.input.java")
        analyze(file.virtualFile)

        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))
        myFixture.editor.caretModel.currentCaret.moveToOffset(120)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))

        myFixture.checkResultByFile("src/quick_fixes/multiple_quick_fixes_on_same_line.expected.java")
    }

    @Test
    fun should_make_the_quick_fix_not_available_after_applying_it() {
        val file = myFixture.configureByFile("src/quick_fixes/single_quick_fix.input.java")
        analyze(file.virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention(diamondQuickFix))

        val availableIntention = myFixture.filterAvailableIntentions(diamondQuickFix)

        assertThat(availableIntention).isEmpty()
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

    private fun analyze(vararg fileToAnalyzes: VirtualFile): List<LiveIssue> {
        val submitter = getService(project, AnalysisSubmitter::class.java)
        submitter.analyzeFilesPreCommit(fileToAnalyzes.toList())
        return fileToAnalyzes.flatMap { getService(project, IssueManager::class.java).getForFile(it) }
    }

    private fun analyzeAll(): List<LiveIssue> {
        val submitter = getService(project, AnalysisSubmitter::class.java)
        submitter.analyzeAllFiles()
        return ProjectUtils.getAllFiles(project).flatMap { getService(project, IssueManager::class.java).getForFile(it) }
    }
}
