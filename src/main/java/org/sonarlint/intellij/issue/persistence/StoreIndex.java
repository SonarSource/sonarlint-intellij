package org.sonarlint.intellij.issue.persistence;

import java.nio.file.Path;
import java.util.Collection;

interface StoreIndex<T> {
  Collection<T> keys();

  void save(T key, Path path);

  void delete(T key);
}
