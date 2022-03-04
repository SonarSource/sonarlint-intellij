package org.sonarlint.intellij.actions.detekt

import java.util.function.BiConsumer

interface InitContext {
    fun <T : Tree> register(cls: Class<T>, visitor: BiConsumer<CheckContext, T>)
}