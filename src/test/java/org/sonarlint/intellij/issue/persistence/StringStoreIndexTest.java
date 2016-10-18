package org.sonarlint.intellij.issue.persistence;

import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class StringStoreIndexTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path baseDir;
  private StringStoreIndex index;

  @Before
  public void setUp() {
    index = new StringStoreIndex(temp.getRoot().toPath());
    baseDir = temp.getRoot().toPath();
  }

  @Test
  public void testSave() {
    Path test1 = baseDir.resolve("p1").resolve("file1");
    index.save("key1", test1);

    assertThat(baseDir.resolve(StringStoreIndex.INDEX_FILENAME)).exists();
    assertThat(index.keys()).containsOnly("key1");
  }

  @Test
  public void testDelete() {
    Path test1 = baseDir.resolve("p1").resolve("file1");
    index.save("key1", test1);
    index.delete("key1");
    index.delete("key2");

    assertThat(baseDir.resolve(StringStoreIndex.INDEX_FILENAME)).exists();
    assertThat(index.keys()).isEmpty();
  }
}
