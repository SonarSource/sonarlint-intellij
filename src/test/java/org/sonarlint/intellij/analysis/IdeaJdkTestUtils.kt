package org.sonarlint.intellij.analysis

import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.pom.java.LanguageLevel

object IdeaJdkTestUtils {

    fun setModuleLanguageLevel(module: Module, level: LanguageLevel?): LanguageLevel? {
        val prev = LanguageLevelUtil.getCustomLanguageLevel(module)
        ModuleRootModificationUtil.updateModel(
            module
        ) { model: ModifiableRootModel ->
            model.getModuleExtension(
                LanguageLevelModuleExtension::class.java
            ).languageLevel = level
        }
        return prev
    }

}
