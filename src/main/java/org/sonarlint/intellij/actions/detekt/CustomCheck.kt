package org.sonarlint.intellij.actions.detekt

import io.gitlab.arturbosch.detekt.api.Rule
import org.sonarlint.intellij.actions.detekt.api.ICheck

/**
 * Time:2022/3/1 3:35 下午
 * Author:dengqu
 * Description:
 */
class CustomCheck(rule: Rule) : ICheck {
    val mRule = rule
    override fun initialize(init: InitContext) {
    }

    override fun getRule(): Rule {
        return mRule
    }

    override fun repositoryName(): String {
        return "detekt"
    }

    override fun language(): String {
        return "kotlin"
    }
}