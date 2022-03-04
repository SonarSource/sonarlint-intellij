package org.sonarlint.intellij.actions.detekt

import java.util.Deque

/**
 * 规则检查上下文相关类
 */
interface CheckContext {
    fun ancestors(): Deque<Tree>

    fun parent(): Tree? {
        return if (this.ancestors().isEmpty()) {
            null
        } else {
            this.ancestors().peek()
        }
    }

    fun filename(): String

    fun fileContent(): String

    fun reportIssue(textRange: TextRange, issue: SIssue)

    fun reportIssue(textRange: HasTextRange, issue: SIssue)
}