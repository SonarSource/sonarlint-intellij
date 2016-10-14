package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.proto.Sonarlint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class IssuePersistentStoreCleanerTest {
  private StoreIndex<String> index;
  private IndexedObjectStore<String, Sonarlint.Issues> store;
  private Function<String, VirtualFile> resolver;
  private IssuePersistentStoreCleaner cleaner;

  @Before
  public void setUp() {
    store = mock(IndexedObjectStore.class);
    index = mock(StoreIndex.class);
    resolver = mock(Function.class);
    cleaner = new IssuePersistentStoreCleaner(mock(Project.class), store, index, resolver);
  }

  @Test
  public void should_clean() throws IOException {
    // mock 2 entries in storage, 1 is a valid file
    when(index.allStorageKeys()).thenReturn(Arrays.asList(new String[]{"key1", "key2"}));
    when(resolver.apply("key1")).thenReturn(mock(VirtualFile.class));

    cleaner.clean();

    verify(store).delete("key2");
    verifyNoMoreInteractions(store);

  }
}
