package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile

@EnabledIf("Rider")
class RiderTests : BaseUiTest() {

    @Test
    fun should_analyze_csharp() = uiTest {
        openExistingProject("sample-csharp")

        openFile("file.cs")

        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.go",
            "Either remove or fill this block of code."
        )
    }

}
