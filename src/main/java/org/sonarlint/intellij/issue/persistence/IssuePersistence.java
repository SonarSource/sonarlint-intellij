package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.proto.Sonarlint;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Writer;
import org.sonarsource.sonarlint.core.util.FileUtils;

public class IssuePersistence extends AbstractProjectComponent {
  private Path storeBasePath;
  private IndexedObjectStore<String, Sonarlint.Issues> store;

  protected IssuePersistence(Project project) {
    super(project);
    storeBasePath = getBasePath();
    StoreIndex<String> index = new StringStoreIndex(storeBasePath);
    PathMapper<String> mapper = new HashingPathMapper(storeBasePath, 2);
    StoreKeyValidator<String> validator = new PathValidator(project);
    Reader<Sonarlint.Issues> reader = is -> {
      try {
        return Sonarlint.Issues.parseFrom(is);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read issues", e);
      }
    };
    Writer<Sonarlint.Issues> writer = (os, issues) -> {
      try {
        issues.writeTo(os);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read issues", e);
      }
    };
    store = new IndexedObjectStore<>(index, mapper, reader, writer, validator);
    store.clean();
  }

  public void save(String key, Sonarlint.Issues issues) throws IOException {
    store.write(key, issues);
  }

  @CheckForNull
  public Sonarlint.Issues read(String key) throws IOException {
    return store.read(key).orElse(null);
  }

  private Path getBasePath() {
    Path ideaDir = new File(myProject.getBaseDir().getPath(), Project.DIRECTORY_STORE_FOLDER).toPath();
    return ideaDir.resolve("sonarlint").resolve("issuestore");
  }

  public void clean() {
    store.clean();
  }

  public void clear() {
    FileUtils.deleteDirectory(storeBasePath);
    FileUtils.forceMkDirs(storeBasePath);
  }
}
