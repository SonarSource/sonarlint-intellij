package org.sonarlint.intellij.actions.detekt

import org.sonarlint.intellij.actions.detekt.api.ICheck

class CheckList {

    private constructor()

    companion object {

        fun allChecks(): List<ICheck> {
            val list = mutableListOf<ICheck>()
            allLoadedRules.forEach {
                list.add(CustomCheck(it))
            }
            return list
        }

        fun getSlangCheck(name: String): ICheck? {
            for (check in allChecks()) {
                if (check.getRule().ruleId == name) {
                    return check
                }
            }
            return null
        }
    }
}