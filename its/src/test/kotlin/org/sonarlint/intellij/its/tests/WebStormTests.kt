package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile

@EnabledIf("isWebStorm")
class WebStormTests : BaseUiTest() {

    @Test
    fun should_analyze_js_ts() = uiTest {
        openExistingProject("sample-js-ts")

        openFile("file.js")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.js",
            "Unexpected comma in middle of array."
        )

        openFile("file2.ts")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file2.ts",
            "Unexpected var, use let or const instead."
        )
    }

}
