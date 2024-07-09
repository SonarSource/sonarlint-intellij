package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile


class IdeaUltimateTests : BaseUiTest() {

    @Test
    @EnabledIf("isIdeaUltimate")
    fun should_analyze_iac() = uiTest {
        openExistingProject("sample-iac")

        openFile("file.yaml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.yaml",
            "Rename tag key \"anycompany:cost-center\" to match the regular expression \"^([A-Z][A-Za-z]*:)*([A-Z][A-Za-z]*)\$\"."
        )

        openFile("Dockerfile")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "Dockerfile",
            "Replace `from` with upper case format `FROM`."
        )

        openFile("kubernetes.yaml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "kubernetes.yaml",
            "Do not use wildcards when defining RBAC permissions."
        )

        openFile("file.tf")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.tf",
            "Rename tag key \"anycompany:cost-center\" to match the regular expression \"^([A-Z][A-Za-z]*:)*([A-Z][A-Za-z]*)\$\"."
        )
    }

    @Test
    @EnabledIf("isIdeaUltimate")
    fun should_analyze_kotlin() = uiTest {
        openExistingProject("sample-kotlin")

        openFile("file.kt")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.kt",
            "Make this interface functional or replace it with a function type."
        )
    }

    @Test
    @EnabledIf("isIdeaUltimate")
    fun should_analyze_xml() = uiTest {
        openExistingProject("sample-xml")

        openFile("file.xml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.xml",
            "Take the required action to fix the issue indicated by this \"FIXME\" comment."
        )
    }

}
