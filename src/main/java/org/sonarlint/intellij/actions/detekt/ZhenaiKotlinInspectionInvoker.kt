package org.sonarlint.intellij.actions.detekt

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Lists
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import io.github.detekt.tooling.api.DetektProvider
import io.github.detekt.tooling.api.DetektProvider.Companion.load
import io.github.detekt.tooling.api.spec.ProcessingSpec
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.UnstableApi
import org.sonarlint.intellij.actions.detekt.DocumentUtils.calculateRealOffset
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayList
import java.util.concurrent.TimeUnit

/**
 * @author dengqu
 * @date 2016/12/13
 */
class ZhenaiKotlinInspectionInvoker(
    private val psiFile: PsiFile,
    private val manager: InspectionManager,
    private val rule: Rule
) {
    val logger = Logger.getInstance(javaClass)
    private var problems = mutableListOf<Finding>()

    @OptIn(UnstableApi::class)
    fun doInvoke() {
        problems.clear()
        println("doInvoke start --------------------------------------------${rule.ruleId}")
        Thread.currentThread().contextClassLoader = javaClass.classLoader
        val start = System.currentTimeMillis()
        val pathList: MutableList<Path> = ArrayList()
        pathList.add(File(psiFile.virtualFile.path).toPath())
        val spec: ProcessingSpec = createSpec(Paths.get(psiFile.virtualFile.path), pathList)
        val resutl = load(
            DetektProvider::class.java.classLoader
        ).get(spec).run()
        resutl?.container?.findings?.let {
            for ((ruleSet, findings) in it) {
                //println("RuleSet: $ruleSet - ${findings.size}")
                findings.forEach(this::reportIssue)
            }
        }
        //println("resutl --------------------------------------------$resutl")
//        logger.debug(
//            "elapsed ${System.currentTimeMillis() - start}ms to" +
//                " to apply rule ${rule.ruleId} for file ${psiFile.virtualFile.canonicalPath}"
//        )
        println("doInvoke end --------------------------------------------${rule.ruleId}")
    }

    private fun reportIssue(issue: Finding) {
        if (issue.id in excludedDuplicates) {
            return
        }
        if (issue.startPosition.line < 0) {
            logger.info("Invalid location for ${issue.compactWithSignature()}.")
            return
        }

        if (rule.ruleId.equals(issue.id)) {
            println("reportIssue ${issue.id} ${issue.issue.id}")
            problems.add(issue)
        }
    }

    fun getRuleProblems(): Array<ProblemDescriptor>? {
        if (problems.isEmpty()) {
            return null
        }
        val problemDescriptors = Lists.newArrayList<ProblemDescriptor>()
        for (problem in problems) {
            val virtualFile = psiFile.virtualFile ?: continue
            val psiFile = PsiManager.getInstance(manager.project).findFile(virtualFile) ?: continue
            val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: continue
            val offsets = Offsets(
                calculateRealOffset(document, problem.charPosition.start, problem.charPosition.end),
                calculateRealOffset(document, problem.charPosition.start, problem.charPosition.end)
            )
            val errorMessage = rule.ruleId
            val problemDescriptor = ProblemsUtils.createProblemDescriptorForPmdRule(
                psiFile,
                manager,
                false,
                rule.ruleId,
                errorMessage,
                problem.charPosition.start,
                problem.charPosition.end,
                problem.startPosition.line
            ) ?: continue
            problemDescriptors.add(problemDescriptor)
        }

        return problemDescriptors.toTypedArray()
    }

    companion object {
        private lateinit var invokers: Cache<FileRule, ZhenaiKotlinInspectionInvoker>

        val smartFoxConfig = ServiceManager.getService(ZhenaiConfig::class.java)!!

        init {
            reInitInvokers(smartFoxConfig.ruleCacheTime)
        }

        private fun doInvokeIfPresent(filePath: String, rule: String) {
            invokers.getIfPresent(FileRule(filePath, rule))?.doInvoke()
        }

        fun refreshFileViolationsCache(file: VirtualFile) {
            ZhenaiLocalInspectionToolProvider.ruleNames.forEach {
                doInvokeIfPresent(file.canonicalPath!!, it)
            }
        }

        fun reInitInvokers(expireTime: Long) {
            invokers = CacheBuilder.newBuilder().maximumSize(500).expireAfterWrite(
                expireTime,
                TimeUnit.MILLISECONDS
            ).build<FileRule, ZhenaiKotlinInspectionInvoker>()!!
        }
    }

    data class FileRule(val filePath: String, val ruleName: String)
    data class Offsets(val start: Int, val end: Int)
}

