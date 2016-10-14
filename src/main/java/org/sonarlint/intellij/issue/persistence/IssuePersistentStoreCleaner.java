package org.sonarlint.intellij.issue.persistence;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Function;
import org.sonarlint.intellij.core.ServerUpdateTask;
import org.sonarlint.intellij.proto.Sonarlint;

/**
 * Deletes from the persistent issue store entries corresponding to files that do not longer exist or are no longer valid.
 */
public class IssuePersistentStoreCleaner {
  private static final Logger LOGGER = Logger.getInstance(ServerUpdateTask.class);
  private final IndexedObjectStore<String, Sonarlint.Issues> store;
  private final StoreIndex<String> index;
  private final Function<String, VirtualFile> relativePathResolver;

  @VisibleForTesting IssuePersistentStoreCleaner(IndexedObjectStore<String, Sonarlint.Issues> store, StoreIndex<String> index,
    Function<String, VirtualFile> relativePathResolver) {
    this.store = store;
    this.index = index;
    this.relativePathResolver = relativePathResolver;
  }

  /**
   * Deletes all entries in the index that correspond to files that do not exist in the project.
   * It assumes that the storage keys are the file paths relative to the project base directory.
   */
  public void clean() {
    LOGGER.info("Cleaning local issue store");
    int counter = 0;
    Collection<String> allFilePaths = index.allStorageKeys();

    for (String p : allFilePaths) {
      VirtualFile vFile = relativePathResolver.apply(p);
      if (vFile == null || !vFile.isValid()) {
        try {
          counter++;
          store.delete(p);
        } catch (IOException e) {
          LOGGER.warn("Failed to delete file in local issue store", e);
        }
      }
    }
    LOGGER.debug(String.format("%d files removed from local issue store", counter));
  }
}
