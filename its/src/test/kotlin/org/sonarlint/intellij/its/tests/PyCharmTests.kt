package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile

@EnabledIf("isPyCharm")
class PyCharmTests : BaseUiTest() {

    @Test
    fun should_analyze_python() = uiTest {
        openExistingProject("sample-python")

        openFile("file.py")

        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.py",
            "Refactor this method to not always return the same value."
        )
    }

}
