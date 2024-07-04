package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile

@EnabledIf("isPhpStorm")
class PhpStormTests : BaseUiTest() {

    @Test
    fun should_analyze_php() = uiTest {
        openExistingProject("sample-php")

        openFile("file.php")

        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.php",
            "Replace the \"var\" keyword with the modifier \"public\"."
        )
    }

}
