/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.ui.page.settings.global

import java.awt.Component
import java.util.concurrent.TimeUnit
import javax.swing.tree.TreeNode
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.config.global.rules.RulesTreeNode
import org.sonarlint.intellij.config.global.rules.RulesTreeTable
import org.sonarlint.intellij.config.global.rules.RulesTreeTableModel
import org.sonarlint.intellij.fixtures.findComponents
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt

/** Page object for the global **Rules** tab. */
class GlobalRulesTabPage internal constructor(
    private val configurable: SonarLintGlobalConfigurable,
    private val tabContent: Component,
) {

    /** Selects [ruleKey] and toggles its activation (same as double-click in the rules tree). */
    fun toggleRuleActivation(ruleKey: String) {
        val rule = awaitRuleNode(ruleKey)
        readOnEdt {
            configurable.selectRule(ruleKey)
            repeat(10) { pumpEdt() }
            val table = tabContent.findComponents<RulesTreeTable>().single()
            (table.model as RulesTreeTableModel).swapAndRefresh(rule)
            repeat(10) { pumpEdt() }
        }
    }

    private fun awaitRuleNode(ruleKey: String): RulesTreeNode.Rule {
        lateinit var rule: RulesTreeNode.Rule
        Awaitility.await()
            .pollInSameThread()
            .atMost(60, TimeUnit.SECONDS)
            .untilAsserted {
                readOnEdt {
                    configurable.selectRule(ruleKey)
                    repeat(30) { pumpEdt() }
                    val table = tabContent.findComponents<RulesTreeTable>().single()
                    rule = findRuleNode(table, ruleKey)
                    assertThat(rule.key).isEqualTo(ruleKey)
                }
            }
        return rule
    }

    private fun findRuleNode(table: RulesTreeTable, ruleKey: String): RulesTreeNode.Rule {
        val root = table.tree.model.root as TreeNode
        val stack = ArrayDeque<TreeNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            if (node is RulesTreeNode.Rule && node.key == ruleKey) {
                return node
            }
            for (i in 0 until node.childCount) {
                stack.add(node.getChildAt(i))
            }
        }
        error("Rule $ruleKey was not found in the rules tree")
    }
}
