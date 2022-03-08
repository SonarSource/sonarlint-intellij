package org.sonarlint.intellij.actions.detekt.api

import io.gitlab.arturbosch.detekt.api.Rule
import org.sonarlint.intellij.actions.detekt.InitContext

/**
 * 规则统一接口
 */
interface ICheck {

    /**
     * 初始化
     *
     * @param init
     */
    fun initialize(init: InitContext)

    /**
     * 获取错误规则描述
     *
     * @return
     */
    fun getRule(): Rule

    fun repositoryName(): String

    fun language(): String
}