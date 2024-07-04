package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile

@EnabledIf("isRubyMine")
class RubyMineTests : BaseUiTest() {

    @Test
    fun should_analyze_ruby() = uiTest {
        openExistingProject("sample-ruby")

        openFile("file.rb")

        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.rb",
            "Define a constant instead of duplicating this literal \"action random1\" 3 times."
        )
    }

}
