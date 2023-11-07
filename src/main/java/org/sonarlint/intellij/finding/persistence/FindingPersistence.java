/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.finding.persistence;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.project.ProjectKt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.tracking.LocalFindingTrackable;
import org.sonarlint.intellij.proto.Sonarlint;
import org.sonarlint.intellij.util.FileUtils;
import org.sonarsource.sonarlint.core.client.legacy.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.client.legacy.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.legacy.objectstore.Writer;

public class FindingPersistence<T extends LiveFinding> {
  private final Path storeBasePath;
  private final IndexedObjectStore<String, Sonarlint.Findings> store;
  private final Project myProject;
  private final String fileNamePrefix;

  public FindingPersistence(Project project, String fileNamePrefix) {
    myProject = project;
    this.fileNamePrefix = fileNamePrefix;

    storeBasePath = getBasePath();
    FileUtils.mkdirs(storeBasePath);
    var index = new StringStoreIndex(storeBasePath);
    var mapper = new HashingPathMapper(storeBasePath, 2);
    var validator = new PathStoreKeyValidator(project.getBaseDir());
    Reader<Sonarlint.Findings> reader = is -> {
      try {
        return Sonarlint.Findings.parseFrom(is);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read findings", e);
      }
    };
    Writer<Sonarlint.Findings> writer = (os, findings) -> {
      try {
        findings.writeTo(os);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to save findings", e);
      }
    };
    store = new IndexedObjectStore<>(index, mapper, reader, writer, validator);
    store.deleteInvalid();
  }

  public synchronized boolean contains(String key) {
    return store.contains(key);
  }

  public void save(String key, Collection<T> findings) throws IOException {
    var findingsToWrite = transform(findings);
    synchronized (this) {
      FileUtils.mkdirs(storeBasePath);
      store.write(key, findingsToWrite);
    }
  }

  public synchronized void clear(String key) throws IOException {
    store.delete(key);
  }

  @CheckForNull
  public synchronized Collection<LocalFindingTrackable> read(String key) {
    var findings = store.read(key);
    return findings.map(FindingPersistence::transform).orElse(null);
  }

  private Path getBasePath() {
    var ideaDir = ProjectKt.getStateStore(myProject).getDirectoryStorePath();
    if (ideaDir == null) {
      ideaDir = new File(myProject.getBasePath(), Project.DIRECTORY_STORE_FOLDER).toPath();
    }
    return ideaDir.resolve("sonarlint/" + fileNamePrefix + "store");
  }

  public synchronized void clear() {
    FileUtils.deleteRecursively(storeBasePath);
    FileUtils.mkdirs(storeBasePath);
  }

  private static Collection<LocalFindingTrackable> transform(Sonarlint.Findings protoFindings) {
    return protoFindings.getFindingList().stream()
      .map(FindingPersistence::transform)
      .toList();
  }

  private Sonarlint.Findings transform(Collection<T> localFindings) {
    var builder = Sonarlint.Findings.newBuilder();
    ReadAction.run(() -> localFindings.stream()
      .filter(LiveFinding::isValid)
      .map(this::transform)
      .forEach(builder::addFinding));

    return builder.build();
  }

  private static LocalFindingTrackable transform(Sonarlint.Findings.Finding finding) {
    return new LocalFindingTrackable(finding);
  }

  private Sonarlint.Findings.Finding transform(T liveFinding) {
    var builder = Sonarlint.Findings.Finding.newBuilder()
      .setRuleKey(liveFinding.getRuleKey())
      .setMessage(liveFinding.getMessage())
      .setResolved(liveFinding.isResolved());

    if (liveFinding.getIntroductionDate() != null) {
      builder.setIntroductionDate(liveFinding.getIntroductionDate());
    }
    if (liveFinding.getLineHash() != null) {
      builder.setChecksum(liveFinding.getLineHash());
    }
    if (liveFinding.getServerFindingKey() != null) {
      builder.setServerFindingKey(liveFinding.getServerFindingKey());
    }
    if (liveFinding.getLine() != null) {
      builder.setLine(liveFinding.getLine());
    }
    var id = liveFinding.getId();
    if (id != null) {
      builder.setId(id.toString());
    }
    return builder.build();
  }
}
