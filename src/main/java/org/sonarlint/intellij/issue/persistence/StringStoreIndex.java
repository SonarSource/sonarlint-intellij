package org.sonarlint.intellij.issue.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.sonarlint.intellij.proto.Sonarlint;

public class StringStoreIndex implements StoreIndex<String> {
  public static final String INDEX_FILENAME = "index.json";
  private final Path storeBasePath;
  private Path indexFilePath;

  public StringStoreIndex(Path storeBasePath) {
    this.storeBasePath = storeBasePath;
    this.indexFilePath = storeBasePath.resolve(INDEX_FILENAME);
  }

  @Override
  public Collection<String> allStorageKeys() {
    return load().keySet();
  }

  private Map<String, String> load() {
    if (!Files.exists(indexFilePath)) {
      return Collections.emptyMap();
    }
    try (InputStream stream = Files.newInputStream(indexFilePath)) {
      return Sonarlint.StorageIndex.parseFrom(stream).getMappedPathByKeyMap();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read local issue store index", e);
    }
  }

  @Override
  public void save(Path absoluteMappedPath, String storageKey) {
    String relativeMappedPath = storeBasePath.relativize(absoluteMappedPath).toString();
    Sonarlint.StorageIndex.Builder builder = Sonarlint.StorageIndex.newBuilder();
    builder.putAllMappedPathByKey(load());
    builder.putMappedPathByKey(storageKey, relativeMappedPath);
    save(builder.build());
  }

  @Override
  public void delete(String storageKey) {
    Sonarlint.StorageIndex.Builder builder = Sonarlint.StorageIndex.newBuilder();
    builder.putAllMappedPathByKey(load());
    builder.removeMappedPathByKey(storageKey);
    save(builder.build());
  }

  private void save(Sonarlint.StorageIndex index) {
    try (OutputStream stream = Files.newOutputStream(indexFilePath)) {
      index.writeTo(stream);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write local issue store index", e);
    }
  }
}
