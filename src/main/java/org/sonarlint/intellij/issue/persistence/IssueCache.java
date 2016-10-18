/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.issue.DefaultIssue;
import org.sonarlint.intellij.issue.IssueMatcher;
import org.sonarlint.intellij.issue.LocalIssuePointer;
import org.sonarlint.intellij.proto.Sonarlint;
import org.sonarlint.intellij.util.SonarLintUtils;

public class IssueCache extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(IssueCache.class);
  static final int MAX_ENTRIES = 100;
  private final Map<VirtualFile, Collection<LocalIssuePointer>> cache;
  private final IssuePersistence store;
  private final IssueMatcher matcher;

  public IssueCache(Project project, IssuePersistence store, IssueMatcher matcher) {
    super(project);
    this.store = store;
    this.matcher = matcher;
    this.cache = new LimitedSizeLinkedHashMap();
  }

  /**
   * Keeps a maximum number of entries in the map. On insertion, if the limit is passed, the entry accessed the longest time ago
   * is flushed into cache and removed from the map.
   */
  private class LimitedSizeLinkedHashMap extends LinkedHashMap<VirtualFile, Collection<LocalIssuePointer>> {
    LimitedSizeLinkedHashMap() {
      super(MAX_ENTRIES, 0.75f, true);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<VirtualFile, Collection<LocalIssuePointer>> eldest) {
      if(size() <= MAX_ENTRIES) {
        return false;
      }

      Sonarlint.Issues issues = transform(eldest.getValue());
      String key = createKey(eldest.getKey());
      try {
        LOGGER.debug("Persisting issues for " + key);
        store.save(key, issues);
      } catch (IOException e) {
        throw new IllegalStateException(String.format("Error persisting issues for %s", key), e);
      }
      return true;
    }
  }

  /**
   * Read issues from a file. If it's not cached, it's loaded from the persisted issues and cached in the process.
   * If no issue is found in disk for the given file, an empty list is returned and also cached
   */
  @CheckForNull
  public synchronized Collection<LocalIssuePointer> read(VirtualFile virtualFile) {
    Collection<LocalIssuePointer> issues = cache.get(virtualFile);
    if (issues != null) {
      return issues;
    }

    return loadToCache(virtualFile);
  }

  public synchronized void save(VirtualFile virtualFile, Collection<LocalIssuePointer> issues) {
    cache.put(virtualFile, Collections.unmodifiableCollection(issues));
  }

  /**
   * Flushes all cached entries to disk.
   * It does not clear the cache.
   */
  public synchronized void flushAll() {
    LOGGER.debug("Persisting all issues");
    cache.forEach((virtualFile, localIssuePointers) -> {
      String key = createKey(virtualFile);
      try {
        store.save(key, transform(localIssuePointers));
      } catch (IOException e) {
        throw new IllegalStateException("Failed to flush cache", e);
      }
    });
  }

  @Override
  public synchronized void projectClosed() {
    flushAll();
  }

  public synchronized void clear() {
    store.clear();
    cache.clear();
  }

  @CheckForNull
  private Collection<LocalIssuePointer> loadToCache(VirtualFile virtualFile) {
    String key = createKey(virtualFile);
    LOGGER.debug("Loading issues for " + key);

    try {
      Sonarlint.Issues protoIssues = store.read(key);
      if (protoIssues == null) {
        return null;
      }
      Collection<LocalIssuePointer> localIssues;
      if (protoIssues.getIssueCount() > 0) {
        localIssues = transform(virtualFile, protoIssues);
      } else {
        localIssues = Collections.emptyList();
      }

      cache.put(virtualFile, localIssues);
      return localIssues;
    } catch (IssueMatcher.NoMatchException e) {
      LOGGER.debug("Failed to match issues", e);
      cache.remove(virtualFile);
      return Collections.emptyList();
    } catch (IOException e) {
      throw new IllegalStateException(String.format("Error loading issues for %s", key), e);
    }
  }

  public synchronized boolean contains(VirtualFile virtualFile) {
    return read(virtualFile) != null;
  }

  private String createKey(VirtualFile virtualFile) {
    return SonarLintUtils.getRelativePath(myProject, virtualFile);
  }

  private Collection<LocalIssuePointer> transform(VirtualFile file, Sonarlint.Issues protoIssues) throws IssueMatcher.NoMatchException {
    PsiFile psiFile = matcher.findFile(file);

    return protoIssues.getIssueList().stream()
      .map(i -> transform(psiFile, i))
      .filter(i -> i != null)
      .collect(Collectors.toList());
  }

  private Sonarlint.Issues transform(Collection<LocalIssuePointer> localIssues) {
    Sonarlint.Issues.Builder builder = Sonarlint.Issues.newBuilder();
    localIssues.stream()
      .map(IssueCache::transform)
      .filter(i -> i != null)
      .forEach(builder::addIssue);

    return builder.build();
  }

  @CheckForNull
  private LocalIssuePointer transform(PsiFile file, Sonarlint.Issues.Issue issue) {
    DefaultIssue newIssue = new DefaultIssue();

    newIssue.setEndLine(issue.getEndLine());
    newIssue.setStartLine(issue.getStartLine());
    newIssue.setStartLineOffset(issue.getStartLineOffset());
    newIssue.setEndLineOffset(issue.getEndLineOffset());

    newIssue.setSeverity(issue.getSeverity());
    newIssue.setRuleKey(issue.getRuleKey());
    newIssue.setRuleName(issue.getRuleName());
    newIssue.setMessage(issue.getMessage());

    try {
      LocalIssuePointer localIssue = matcher.match(file, newIssue);

      localIssue.setServerIssueKey(issue.getServerIssueKey());
      localIssue.setLineHash(issue.getChecksum());
      localIssue.setAssignee(issue.getAssignee());
      localIssue.setResolved(issue.getResolved());
      localIssue.setCreationDate(issue.getCreationDate());
      return localIssue;
    } catch (IssueMatcher.NoMatchException e) {
      LOGGER.debug("Failed to match issues", e);
      return null;
    }
  }

  @CheckForNull
  private static Sonarlint.Issues.Issue transform(LocalIssuePointer localIssue) {
    Sonarlint.Issues.Issue.Builder builder = Sonarlint.Issues.Issue.newBuilder()
      .setRuleKey(localIssue.getRuleKey())
      .setRuleName(localIssue.ruleName())
      .setAssignee(localIssue.getAssignee())
      .setMessage(localIssue.getMessage())
      .setResolved(localIssue.isResolved())
      .setSeverity(localIssue.severity());

    if(localIssue.getCreationDate() != null) {
      builder.setCreationDate(localIssue.getCreationDate());
    }
    if(localIssue.getLineHash() != null) {
      builder.setChecksum(localIssue.getLineHash());
    }
    if(localIssue.getServerIssueKey() != null) {
      builder.setServerIssueKey(localIssue.getServerIssueKey());
    }

    RangeMarker range = localIssue.range();
    if (range != null) {
      Document doc = range.getDocument();

      int startLine = doc.getLineNumber(range.getStartOffset());
      int endLine = doc.getLineNumber(range.getEndOffset());
      builder.setStartLine(startLine)
        .setEndLine(endLine)
        .setStartLineOffset(range.getStartOffset() - doc.getLineStartOffset(startLine))
        .setEndLineOffset(range.getStartOffset() - doc.getLineStartOffset(startLine));
    }
    return builder.build();
  }
}
