package org.sonarlint.intellij.actions.detekt

import org.sonarlint.intellij.actions.detekt.api.ICheck
import java.io.IOException
import java.util.ArrayList
import java.util.Deque
import java.util.function.BiConsumer

class ChecksVisitor(checks: List<ICheck>) :
    TreeVisitor<InputFileContext>() {

    private val problems: MutableList<Problem>

    init {
        problems = ArrayList()
    }

    fun getProblems(): List<Problem> {
        return problems
    }

    inner class ContextAdapter : InitContext, CheckContext {

        private var currentCtx: InputFileContext? = null

        override fun <T : Tree> register(cls: Class<T>, visitor: BiConsumer<CheckContext, T>) {
            this@ChecksVisitor.register(cls) { ctx, tree ->
                currentCtx = ctx
                visitor.accept(this, tree)
            }
        }

        override fun ancestors(): Deque<Tree> {
            return currentCtx!!.ancestors()
        }

        override fun filename(): String {
            return currentCtx!!.inputFile!!.filename()
        }

        override fun fileContent(): String {
            try {
                return currentCtx!!.inputFile!!.contents()
            } catch (e: IOException) {
                throw IllegalStateException("Cannot read content of " + currentCtx!!.inputFile, e)
            }
        }

        override fun reportIssue(textRange: TextRange, issue: SIssue) {
            doReportIssue(textRange, issue)
        }

        override fun reportIssue(textRange: HasTextRange, issue: SIssue) {
            doReportIssue(textRange.textRange(), issue)
        }

        private fun doReportIssue(textRange: TextRange?, issue: SIssue) {
            val problem = Problem()
            problem.issue = issue
            problem.textRange = textRange!!
            problems.add(problem)
        }

        override fun parent(): Tree? {
            return if (this.ancestors().isEmpty()) {
                null
            } else {
                this.ancestors().peek()
            }
        }
    }

    inner class Problem {
        lateinit var issue: SIssue
        lateinit var textRange: TextRange
    }
}
