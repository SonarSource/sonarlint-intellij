package org.sonarlint.intellij.issue.persistence;

import java.nio.file.Path;
import java.util.Collection;

public interface StoreIndex<T> {
  Collection<T> allStorageKeys();

  void save(Path absoluteMappedPath, T key);

  void delete(T key);
}
