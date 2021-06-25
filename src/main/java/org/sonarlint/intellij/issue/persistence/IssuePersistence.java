/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.issue.LocalIssueTrackable;
import org.sonarlint.intellij.proto.Sonarlint;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Writer;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;

public class IssuePersistence {
  private final Path storeBasePath;
  private final IndexedObjectStore<String, Sonarlint.Issues> store;
  private final Project myProject;

  protected IssuePersistence(Project project) {
    myProject = project;

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

  public synchronized void save(String key, Collection<LiveIssue> issues) throws IOException {
    FileUtils.mkdirs(storeBasePath);
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

  private static Sonarlint.Issues transform(Collection<LiveIssue> localIssues) {
    Sonarlint.Issues.Builder builder = Sonarlint.Issues.newBuilder();
    ReadAction.run(() -> localIssues.stream()
      .filter(LiveIssue::isValid)
      .map(IssuePersistence::transform)
      .forEach(builder::addIssue));

    return builder.build();
  }

  private static LocalIssueTrackable transform(Sonarlint.Issues.Issue issue) {
    return new LocalIssueTrackable(issue);
  }

  private static Sonarlint.Issues.Issue transform(LiveIssue liveIssue) {
    Sonarlint.Issues.Issue.Builder builder = Sonarlint.Issues.Issue.newBuilder()
      .setRuleKey(liveIssue.getRuleKey())
      .setMessage(liveIssue.getMessage())
      .setResolved(liveIssue.isResolved());

    if (liveIssue.getAssignee() != null) {
      builder.setAssignee(liveIssue.getAssignee());
    }
    if (liveIssue.getCreationDate() != null) {
      builder.setCreationDate(liveIssue.getCreationDate());
    }
    if (liveIssue.getLineHash() != null) {
      builder.setChecksum(liveIssue.getLineHash());
    }
    if (liveIssue.getServerIssueKey() != null) {
      builder.setServerIssueKey(liveIssue.getServerIssueKey());
    }
    if (liveIssue.getLine() != null) {
      builder.setLine(liveIssue.getLine());
    }
    return builder.build();
  }
}
