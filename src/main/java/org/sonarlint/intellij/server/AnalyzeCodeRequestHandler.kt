package org.sonarlint.intellij.server

import org.sonarlint.intellij.analysis.DefaultClientInputFile
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.EngineManager
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration
import org.sonarsource.sonarlint.core.commons.Language
import java.io.File

class AnalyzeCodeRequestHandler {
    fun analyze(codeSnippet: String, language: String): List<Issue> {
        val codeFile = File.createTempFile("file", "")
        codeFile.writeText(codeSnippet)
        codeFile.deleteOnExit()


        val issues = mutableListOf<Issue>()
        val inputFile = DefaultClientInputFile(codeFile.toPath(), codeSnippet, Language.valueOf(language))
        getService(EngineManager::class.java).standaloneEngine.analyze(
            StandaloneAnalysisConfiguration.builder()
                .addInputFile(inputFile)
                .setBaseDir(codeFile.parentFile.toPath())
                .build(), issues::add, GlobalLogOutput.get(), null
        )
        return issues
    }

}
