package org.sonarlint.intellij.actions.detekt

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

/**
 * @author dengqu
 * @date 2017/02/28
 */
class DelegateKotlinInspection : LocalInspectionTool(), ZhenaiBaseInspection {


    private val ruleName: String? = null

    private val aliPmdInspection: ZhenaiKotlinInspection

    init {
        //System.out.println("ruleName =" + ruleName)
        aliPmdInspection = ZhenaiKotlinInspection(ruleName!!)
    }

    override fun runForWholeFile(): Boolean {
        return aliPmdInspection.runForWholeFile()
    }

    override fun checkFile(
        file: PsiFile, manager: InspectionManager,
        isOnTheFly: Boolean
    ): Array<ProblemDescriptor>? {
        return aliPmdInspection.checkFile(file, manager, isOnTheFly)
    }

    override fun getStaticDescription(): String? {
        return aliPmdInspection.staticDescription
    }

    override fun ruleName(): String {
        return ruleName!!
    }

    @Nls
    override fun getDisplayName(): String {
        return aliPmdInspection.displayName
    }

    override fun getDefaultLevel(): HighlightDisplayLevel {
        return aliPmdInspection.defaultLevel
    }

    @Nls
    override fun getGroupDisplayName(): String {
        return aliPmdInspection.groupDisplayName
    }

    override fun isEnabledByDefault(): Boolean {
        return aliPmdInspection.isEnabledByDefault
    }

    override fun getShortName(): String {
        return aliPmdInspection.shortName
    }

    override fun isSuppressedFor(element: PsiElement): Boolean {
        return false
    }
}
