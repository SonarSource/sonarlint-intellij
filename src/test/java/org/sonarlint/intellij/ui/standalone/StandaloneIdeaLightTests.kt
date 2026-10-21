/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.standalone

import com.intellij.ide.util.PropertiesComponent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.config.global.SonarLintGlobalSettingsStore
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Light UI coverage for former Standalone UI IT scenarios (removed in SLI-2990).
 */
class StandaloneIdeaLightTests : AbstractSonarLintUILightTests() {

    @BeforeEach
    fun waitForReady() {
        waitUntilAnalysisReady()
    }

    @Test
    fun should_exclude_rule_and_focus_on_new_code() {
        val file = openFileFromTestData("src/Foo.java")
        mainToolWindow.findings.toolbar.analyzeCurrentFileButton.click(file)

        awaitUntilAsserted {
            assertThat(mainToolWindow.findings.issues.map { it.ruleKey }).contains(RULE_S2094)
        }

        setGlobalRuleActive(RULE_S2094, active = false)

        awaitUntilAsserted {
            assertThat(mainToolWindow.findings.emptyStateText).isIn(NO_FINDINGS_EMPTY_STATES)
        }

        setGlobalRuleActive(RULE_S2094, active = true)

        mainToolWindow.findings.filters.setFocusOnNewCode(true)
        analyzeFilesForReport(file)

        awaitUntilAsserted(timeoutSeconds = 60) {
            val report = mainToolWindow.reportTab()
            assertThat(report.containsAllTexts("Found 1 new issue from last 30 days")).isTrue()
            assertThat(report.containsAllTexts("No older issues")).isTrue()
        }

        mainToolWindow.findings.toolbar.analyzeCurrentFileButton.click(file)

        awaitUntilAsserted(timeoutSeconds = 60) {
            assertThat(mainToolWindow.findings.issuesSummaryButtonText).contains("1")
            assertThat(mainToolWindow.findings.issues.map { it.message })
                .anyMatch { it.contains("Remove this empty class") }
        }

        mainToolWindow.findings.filters.resetFocusOnNewCode()
    }

    @Test
    fun should_exclude_file_and_analyze_file_and_no_issues_found() {
        setProjectLevelExclusions(listOf("FILE:src/Bar.java"))

        val bar = openFileFromTestData("src/Bar.java")
        mainToolWindow.findings.toolbar.analyzeCurrentFileButton.click(bar)

        awaitUntilAsserted {
            assertThat(mainToolWindow.findings.emptyStateText).isIn(NO_FINDINGS_EMPTY_STATES)
        }

        setProjectLevelExclusions(emptyList())
    }

    @Test
    fun should_analyze_ansible() {
        // Same fixture as UI IT (`its/projects/DuplicatedEnvsChart`). Helm evaluation does not resolve in light
        // tests (SonarLint FS); materialize the template as IT would after rendering with values.yaml.
        val chartRoot = Path.of("its/projects/DuplicatedEnvsChart").toAbsolutePath()
        copyDirectoryIntoModuleScopeRoot(chartRoot)
        waitUntilAnalysisReady()
        writeModuleScopeFile(
            // Not under `templates/` so IaC treats this as plain Kubernetes YAML (Helm dir resolution fails in light tests).
            "memory_limit_pod2-evaluated.yml",
            """
            apiVersion: v1
            kind: Pod
            metadata:
              name: example
              namespace: with-global-limit2
            spec:
              automountServiceAccountToken: true
              containers:
                - name: memory-limit
                  image: nginx:1.2.3
                  resources:
                    limits:
                      memory: 1G
                      cpu: 600m
                    request:
                      memory: 512Mi
                      cpu: 200m
            """.trimIndent() + "\n",
        )
        val yaml = openModuleScopeRelativeFile("memory_limit_pod2-evaluated.yml")
        mainToolWindow.findings.toolbar.analyzeCurrentFileButton.click(yaml)

        awaitUntilAsserted(timeoutSeconds = 60) {
            assertThat(mainToolWindow.findings.issues.map { it.message })
                .anyMatch {
                    it.contains("Bind this resource's automounted service account to RBAC or disable automounting.")
                }
        }
    }

    @Test
    fun should_not_open_walkthrough_after_opened_once() {
        getGlobalSettings().setHasWalkthroughRunOnce(true)
        PropertiesComponent.getInstance().setValue(HAS_WALKTHROUGH_RUN_ONCE, true)

        val walkthrough = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            .getToolWindow(org.sonarlint.intellij.ui.page.WALKTHROUGH_TOOL_WINDOW_ID)

        assertThat(walkthrough == null || !walkthrough.isVisible).isTrue()
    }

    private fun setGlobalRuleActive(ruleKey: String, active: Boolean) {
        val globals = getGlobalSettings()
        if (active) {
            globals.enableRule(ruleKey)
        } else {
            globals.disableRule(ruleKey)
        }
        getService(SonarLintGlobalSettingsStore::class.java).save(globals)
        SonarLintGlobalConfigurable.triggerAnalysis()
    }

    private companion object {
        private const val RULE_S2094 = "java:S2094"
        private const val HAS_WALKTHROUGH_RUN_ONCE = "hasWalkthroughRunOnce"
        private val NO_FINDINGS_EMPTY_STATES = listOf("No findings to display", "Nothing to show")
    }
}
