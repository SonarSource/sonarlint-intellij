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

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.AnalysisCallback
import org.sonarlint.intellij.analysis.AnalysisReadinessCache
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

@DisabledOnOs(OS.WINDOWS)
class StandaloneModeMediumTests : AbstractSonarLintLightTests() {

    @BeforeEach
    fun waitForReady() {
        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            assertThat(getService(project, AnalysisReadinessCache::class.java).isModuleReady(module)).isTrue()
        }
    }

    @Test
    fun should_analyze_java_file() {
        val issues = analyzeFile("src/Main.java")
        assertThat(issues.map { it.getRuleKey() }).contains("java:S106")
    }

    @Test
    fun should_analyze_xml_file() {
        val issues = analyzeFile("src/file.xml")
        assertThat(issues.map { it.getRuleKey() }).contains("xml:S1778")
    }

    @Test
    fun should_analyze_css_file() {
        val issues = analyzeFile("src/style.css")
        assertThat(issues.map { it.getRuleKey() }).contains("css:S4647")
    }

    @Test
    fun should_analyze_dockerfile() {
        val issues = analyzeFile("src/Dockerfile")
        assertThat(issues.map { it.getRuleKey() }).contains("docker:S6476")
    }

    @Test
    fun should_analyze_python_file() {
        val issues = analyzeFile("src/file.py")
        assertThat(issues.map { it.getRuleKey() }).contains("python:S3516")
    }

    @Test
    fun should_analyze_cloudformation_file() {
        val issues = analyzeFile("src/CloudFormation.yaml")
        assertThat(issues.map { it.getRuleKey() })
            .satisfiesAnyOf(
                { assertThat(it).contains("cloudformation:S6295") },
                { assertThat(it).contains("yaml:S1135") }
            )
    }

    @Test
    fun should_analyze_kubernetes_file() {
        val issues = analyzeFile("src/k8s.yaml")
        assertThat(issues.map { it.getRuleKey() }).contains("cloudformation:S4423")
    }

    @Test
    fun should_analyze_terraform_file() {
        val issues = analyzeFile("src/Terraform.tf")
        assertThat(issues.map { it.getRuleKey() }).contains("terraform:S4423")
    }

    @Test
    fun should_analyze_html_file() {
        val issues = analyzeFile("src/file.html")
        assertThat(issues.map { it.getRuleKey() }).contains("Web:UnsupportedTagsInHtml5Check")
    }

    @Test
    fun should_analyze_kotlin_file() {
        val issues = analyzeFile("src/file.kt")
        assertThat(issues.map { it.getRuleKey() }).contains("kotlin:S1481")
    }

    @Test
    fun should_analyze_php_file() {
        val issues = analyzeFile("src/file.php")
        assertThat(issues.map { it.getRuleKey() }).contains("php:S1780")
    }

    @Test
    fun should_analyze_ruby_file() {
        val issues = analyzeFile("src/file.rb")
        assertThat(issues.map { it.getRuleKey() }).contains("ruby:S1481")
    }

    private fun analyzeFile(relativePath: String): Collection<LiveIssue> {
        val file = createRealFile(relativePath)
        val resultRef = AtomicReference<AnalysisResult>()
        val errorRef = AtomicReference<Throwable>()

        getService(project, AnalysisSubmitter::class.java).analyzeFilesWithCallback(
            setOf(file),
            object : AnalysisCallback {
                override fun onSuccess(result: AnalysisResult) {
                    resultRef.set(result)
                }

                override fun onError(e: Throwable) {
                    e.printStackTrace()
                    errorRef.set(e)
                }
            }
        )

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until {
            resultRef.get() != null || errorRef.get() != null
        }

        if (errorRef.get() != null) {
            throw RuntimeException("Analysis failed for $relativePath", errorRef.get())
        }

        val issues = resultRef.get().findings.issuesPerFile[file] ?: emptyList()

        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.DELETED, file))),
            true
        )
        
        return issues
    }

    private fun createRealFile(relativePath: String): VirtualFile {
        val sourcePath = Path.of(testDataPath, relativePath).toAbsolutePath()
        if (!Files.exists(sourcePath)) {
            throw RuntimeException("Test file not found: $sourcePath")
        }
        val content = Files.readString(sourcePath)

        val psiFile = createTestPsiFile(relativePath, content)
        val virtualFile = psiFile.virtualFile

        getService(BackendService::class.java).updateFileSystem(
            mapOf(module to listOf(VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile))),
            true
        )
        
        // Give backend time to process
        Thread.sleep(3000)
        
        return virtualFile
    }

}
