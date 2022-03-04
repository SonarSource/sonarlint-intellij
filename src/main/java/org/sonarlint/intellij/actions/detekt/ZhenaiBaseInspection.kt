package org.sonarlint.intellij.actions.detekt

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * @author dengqu
 * @date 2016/12/08
 */
interface ZhenaiBaseInspection {

    /**
     * ruleName

     * @return ruleName
     */
    fun ruleName(): String

    /**
     * display info for inspection

     * @return display
     */
    fun getDisplayName(): String

    /**
     * group display info for inspection

     * @return group display
     */
    fun getGroupDisplayName(): String

    /**
     * inspection enable by default

     * @return true -> enable
     */
    fun isEnabledByDefault(): Boolean

    /**
     * default inspection level

     * @return level
     */
    fun getDefaultLevel(): HighlightDisplayLevel

    /**
     * inspection short name

     * @return shor name
     */
    fun getShortName(): String

    fun manualBuildFix(psiElement: PsiElement, isOnTheFly: Boolean): LocalQuickFix? = null

    fun manualParsePsiElement(
        psiFile: PsiFile, manager: InspectionManager,
        start: Int, end: Int
    ): PsiElement {
        return psiFile.findElementAt(start)!!
    }

    companion object {
        val GROUP_NAME = "Zhenai-Pmd"
        val GROUP_NAME1 = "Zhenai-Lint"
    }
}
