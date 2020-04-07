/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.issue.LocalIssueTrackable;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarlint.intellij.proto.Sonarlint;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Writer;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;

public class IssuePersistence extends AbstractProjectComponent {
  private final Path storeBasePath;
  private final IndexedObjectStore<String, Sonarlint.Issues> store;

  protected IssuePersistence(Project project) {
    super(project);
    storeBasePath = getBasePath();
    FileUtils.mkdirs(storeBasePath);
    StoreIndex<String> index = new StringStoreIndex(storeBasePath);
    PathMapper<String> mapper = new HashingPathMapper(storeBasePath, 2);
    StoreKeyValidator<String> validator = new PathStoreKeyValidator(project.getBaseDir());
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
        throw new IllegalStateException("Failed to save issues", e);
      }
    };
    store = new IndexedObjectStore<>(index, mapper, reader, writer, validator);
    store.deleteInvalid();
  }

  public synchronized boolean contains(String key) {
    return store.contains(key);
  }

  public synchronized void save(String key, Collection<? extends Trackable> issues) throws IOException {
    store.write(key, transform(issues));
  }

  public synchronized void clear(String key) throws IOException {
    store.delete(key);
  }

  @CheckForNull
  public synchronized Collection<LocalIssueTrackable> read(String key) throws IOException {
    Optional<Sonarlint.Issues> issues = store.read(key);
    return issues.map(IssuePersistence::transform).orElse(null);
  }

  private Path getBasePath() {
    Path ideaDir = new File(myProject.getBasePath(), Project.DIRECTORY_STORE_FOLDER).toPath();
    return ideaDir.resolve("sonarlint").resolve("issuestore");
  }

  public synchronized void clean() {
    store.deleteInvalid();
  }

  public synchronized void clear() {
    FileUtils.deleteRecursively(storeBasePath);
    FileUtils.mkdirs(storeBasePath);
  }

  private static Collection<LocalIssueTrackable> transform(Sonarlint.Issues protoIssues) {
    return protoIssues.getIssueList().stream()
      .map(IssuePersistence::transform)
      .collect(Collectors.toList());
  }

  private static Sonarlint.Issues transform(Collection<? extends Trackable> localIssues) {
    Sonarlint.Issues.Builder builder = Sonarlint.Issues.newBuilder();
    localIssues.stream()
      .map(IssuePersistence::transform)
      .forEach(builder::addIssue);

    return builder.build();
  }

  private static LocalIssueTrackable transform(Sonarlint.Issues.Issue issue) {
    return new LocalIssueTrackable(issue);
  }

  @CheckForNull
  private static Sonarlint.Issues.Issue transform(Trackable localIssue) {
    Sonarlint.Issues.Issue.Builder builder = Sonarlint.Issues.Issue.newBuilder()
      .setRuleKey(localIssue.getRuleKey())
      .setMessage(localIssue.getMessage())
      .setResolved(localIssue.isResolved());

    if (localIssue.getAssignee() != null) {
      builder.setAssignee(localIssue.getAssignee());
    }
    if (localIssue.getCreationDate() != null) {
      builder.setCreationDate(localIssue.getCreationDate());
    }
    if (localIssue.getLineHash() != null) {
      builder.setChecksum(localIssue.getLineHash());
    }
    if (localIssue.getServerIssueKey() != null) {
      builder.setServerIssueKey(localIssue.getServerIssueKey());
    }
    if (localIssue.getLine() != null) {
      builder.setLine(localIssue.getLine());
    }
    return builder.build();
  }
}
