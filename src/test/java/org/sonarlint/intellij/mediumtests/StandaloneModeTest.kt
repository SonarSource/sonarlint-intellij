/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.jetbrains.annotations.NotNull
import org.junit.Before
import org.junit.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisCallback
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.SonarLintEngineManager
import org.sonarlint.intellij.issue.IssueManager
import org.sonarlint.intellij.issue.LiveIssue
import org.sonarlint.intellij.trigger.SonarLintSubmitter
import org.sonarlint.intellij.trigger.TriggerType

class StandaloneModeTest : AbstractSonarLintLightTests() {

    @Before
    fun prepare() {
        getService(SonarLintEngineManager::class.java).stopAllEngines(false)
    }

    @Test
    fun should_analyze_java_file() {
        val fileToAnalyze = myFixture.configureByFile("src/Main.java").virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.ruleName },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("java:S1220", "The default unnamed package should not be used", null),
                tuple("java:S106", "Standard outputs should not be used directly to log anything", Pair(67, 77))
            )
    }

    @Test
    fun should_find_cross_file_python_issue() {
        val fileToAnalyze = myFixture.configureByFiles("src/main.py", "src/mod.py").first().virtualFile

        val issues = analyze(fileToAnalyze)

        assertThat(issues)
            .extracting(
                { it.ruleKey },
                { it.ruleName },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("python:S930", "The number and name of arguments passed to a function should match its parameters", Pair(45, 48))
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
                { it.ruleName },
                { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
            .containsExactlyInAnyOrder(
                tuple("python:S930", "The number and name of arguments passed to a function should match its parameters", Pair(21, 24))
            )
    }

    @Test
    fun should_find_secrets_excluding_vcs_ignored_files() {
        val fileToAnalyze = myFixture.configureByFile("src/devenv.js").virtualFile
        val fileToAnalyze_ignored = myFixture.configureByFile("src/devenv_ignored.js").virtualFile
        val fileToAnalyze_unversionned = myFixture.configureByFile("src/devenv_unversionned.js").virtualFile

        val myVcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
        val myVcs = MockAbstractVcs(project)
        try {
            myVcs.setChangeProvider(MyMockChangeProvider())
            myVcsManager.registerVcs(myVcs)
            myVcsManager.setDirectoryMapping("", myVcs.getName())
            myVcsManager.waitForInitialized()


            val myChangeListManager = ChangeListManagerImpl.getInstanceImpl(project)
            val dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
            // Wait for the initial refresh status job to complete on the root directory
            myChangeListManager.waitEverythingDoneInTestMode()

            // Now force a specific status update for some files. It will ask the MyMockChangeProvider
            dirtyScopeManager.fileDirty(fileToAnalyze_ignored)
            dirtyScopeManager.fileDirty(fileToAnalyze_unversionned)
            myChangeListManager.waitEverythingDoneInTestMode()

            // Ensure previous code worked as expected
            assertThat(myChangeListManager.isIgnoredFile(fileToAnalyze_ignored)).isTrue()
            assertThat(myChangeListManager.isUnversioned(fileToAnalyze_unversionned)).isTrue()

            val issues = analyze(fileToAnalyze, fileToAnalyze_ignored, fileToAnalyze_unversionned, triggerType = TriggerType.ALL)

            assertThat(issues)
                .extracting(
                    { it.psiFile().name },
                    { it.ruleKey },
                    { it.ruleName },
                    { issue -> issue.range?.let { Pair(it.startOffset, it.endOffset) } })
                .containsExactlyInAnyOrder(
                    tuple("devenv.js", "secrets:S6290", "AWS Access Key ID", Pair(286, 306)),
                    tuple("devenv.js", "javascript:S2703", "Variables should be declared explicitly", Pair(62, 72)),
                    tuple("devenv_unversionned.js", "secrets:S6290", "AWS Access Key ID", Pair(286, 306)),
                    tuple("devenv_unversionned.js", "javascript:S2703", "Variables should be declared explicitly", Pair(62, 72))
                )
        } finally {
            myVcsManager.unregisterVcs(myVcs)
        }
    }

    @Test
    fun should_apply_quick_fix_on_original_range_when_no_code_is_modified() {
        val file = myFixture.configureByFile("src/quick_fixes/single_quick_fix.input.java")
        analyze(file.virtualFile)

        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove type argument(s)"))

        myFixture.checkResultByFile("src/quick_fixes/single_quick_fix.expected.java")
    }

    @Test
    fun should_apply_quick_fix_on_adapted_range_when_code_is_modified_within_the_range() {
        val file = myFixture.configureByFile("src/quick_fixes/single_quick_fix.input.java")
        analyze(file.virtualFile)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)

        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove type argument(s)"))

        myFixture.checkResultByFile("src/quick_fixes/single_quick_fix.expected.java")
    }

    @Test
    fun should_apply_multiple_quick_fixes_on_different_lines() {
        val file = myFixture.configureByFile("src/quick_fixes/multiple_quick_fixes_on_different_lines.input.java")
        analyze(file.virtualFile)

        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove type argument(s)"))
        myFixture.editor.caretModel.currentCaret.moveToOffset(140)
        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove type argument(s)"))

        myFixture.checkResultByFile("src/quick_fixes/multiple_quick_fixes_on_different_lines.expected.java")
    }

    @Test
    fun should_apply_overlapping_quick_fixes_on_same_line() {
        val file = myFixture.configureByFile("src/quick_fixes/overlapping_quick_fixes.input.java")
        analyze(file.virtualFile)

        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove type argument(s)"))
        myFixture.editor.caretModel.currentCaret.moveToOffset(58)
        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove \"strings\"."))

        myFixture.checkResultByFile("src/quick_fixes/overlapping_quick_fixes.expected.java")
    }

    @Test
    fun should_apply_multiple_quick_fixes_on_same_line() {
        val file = myFixture.configureByFile("src/quick_fixes/multiple_quick_fixes_on_same_line.input.java")
        analyze(file.virtualFile)

        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove type argument(s)"))
        myFixture.editor.caretModel.currentCaret.moveToOffset(120)
        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove type argument(s)"))

        myFixture.checkResultByFile("src/quick_fixes/multiple_quick_fixes_on_same_line.expected.java")
    }

    @Test
    fun should_make_the_quick_fix_not_available_after_applying_it() {
        val file = myFixture.configureByFile("src/quick_fixes/single_quick_fix.input.java")
        analyze(file.virtualFile)
        myFixture.launchAction(myFixture.findSingleIntention("SonarLint: Remove type argument(s)"))

        val availableIntention = myFixture.filterAvailableIntentions("SonarLint: Remove type argument(s)")

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
            for (path in dirtyScope.getDirtyFiles()) {
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

    private fun analyze(vararg fileToAnalyzes: VirtualFile, triggerType: TriggerType = TriggerType.ACTION): List<LiveIssue> {
        val submitter = getService(project, SonarLintSubmitter::class.java)
        submitter.submitFiles(fileToAnalyzes.toList(), triggerType, EmptyAnalysisCallback(), false)
        return fileToAnalyzes.flatMap { getService(project, IssueManager::class.java).getForFile(it) }
    }

    class EmptyAnalysisCallback : AnalysisCallback {
        override fun onSuccess(failedVirtualFiles: MutableSet<VirtualFile>) {
        }

        override fun onError(e: Throwable) {
        }

    }
}
