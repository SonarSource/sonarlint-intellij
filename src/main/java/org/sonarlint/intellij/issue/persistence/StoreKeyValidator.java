package org.sonarlint.intellij.issue.persistence;

import java.util.function.Function;

interface StoreKeyValidator<K> extends Function<K, Boolean> {
}
