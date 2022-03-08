/*
 * Copyright 1999-2017 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sonarlint.intellij.actions.detekt

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import io.gitlab.arturbosch.detekt.api.Rule
import org.sonarlint.intellij.actions.detekt.api.ICheck
import org.sonarlint.intellij.config.Settings

/**
 * @author dengqu
 * @date 2016/12/16
 */
class ZhenaiKotlinInspection(private val ruleName: String) : LocalInspectionTool(),
    ZhenaiBaseInspection {
    override fun manualBuildFix(psiElement: PsiElement, isOnTheFly: Boolean): LocalQuickFix? {
        return null
    }

    private val staticDescription: String

    private val displayName: String


    private val defaultLevel: HighlightDisplayLevel

    private val check: ICheck

    init {
        check = CheckList.getSlangCheck(ruleName)!!
        displayName = check.getRule().ruleId
        staticDescription = check.getRule().ruleId
        defaultLevel = HighlightDisplayLevels.MAJOR
    }

    override fun runForWholeFile(): Boolean {
        return true
    }

    override fun checkFile(
        file: PsiFile, manager: InspectionManager,
        isOnTheFly: Boolean
    ): Array<ProblemDescriptor>? {
        if (file == null || !file.virtualFile.canonicalPath!!.endsWith(".kt")) {
            return null
        }
        val isActive =
            Settings.getGlobalSettings().detektRulesByKey.get("${check.repositoryName()}:${check.getRule().ruleId}")?.isActive ?: true
        println("checkFile ${check.getRule().ruleId} $isOnTheFly $isActive")
        if (!isActive) {
            return null
        }
        val invoker = ZhenaiKotlinInspectionInvoker(file, manager, check.getRule())
        invoker.doInvoke()
        return invoker.getRuleProblems()
    }

    override fun getStaticDescription(): String? {
        return staticDescription
    }

    override fun ruleName(): String {
        return ruleName
    }

    @Nls
    override fun getDisplayName(): String {
        return displayName
    }

    override fun getDefaultLevel(): HighlightDisplayLevel {
        return defaultLevel
    }

    @Nls
    override fun getGroupDisplayName(): String {
        return ZhenaiBaseInspection.GROUP_NAME1
    }

    override fun isEnabledByDefault(): Boolean {
        return true
    }

    override fun isSuppressedFor(element: PsiElement): Boolean {
        return false
    }

    override fun getShortName(): String {

        var shortName = "Alibaba" + check.getRule().ruleId
        val index = shortName.lastIndexOf("Rule")
        if (index > NumberConstants.INDEX_0) {
            shortName = shortName.substring(NumberConstants.INDEX_0, index)
        }
        return shortName
    }
}
