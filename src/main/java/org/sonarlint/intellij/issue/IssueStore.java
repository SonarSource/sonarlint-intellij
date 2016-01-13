/**
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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.sonar.runner.api.Issue;

import javax.annotation.CheckForNull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores issues associated to a {@link PsiElement}, {@link RangeMarker} or  {@link PsiFile}.
 * Issues can then be displayed as annotations at any time.
 */
@ThreadSafe
public class IssueStore extends AbstractProjectComponent {
  private static final long THRESHOLD = 10_000;
  private final Map<VirtualFile, Collection<StoredIssue>> storePerFile;

  public IssueStore(Project project) {
    super(project);
    this.storePerFile = new ConcurrentHashMap<>();
  }

  public void clear() {
    storePerFile.clear();
  }

  private long getNumberIssues() {
    long count = 0;
    Iterator<Map.Entry<VirtualFile, Collection<StoredIssue>>> it = storePerFile.entrySet().iterator();

    while (it.hasNext()) {
      count += it.next().getValue().size();
    }

    return count;
  }

  @Override
  public void disposeComponent() {
    clear();
  }

  public Collection<StoredIssue> getForFile(VirtualFile file) {
    Collection<StoredIssue> issues = storePerFile.get(file);
    return issues == null ? Collections.<StoredIssue>emptyList() : issues;
  }

  public void clearFile(VirtualFile file) {
    storePerFile.remove(file);
  }

  /**
   * Clears issues in the File if the threshold of issues per project as been passed.
   * It could be improved by being fully synchronized and deleting the oldest file closed.
   */
  public void clean(VirtualFile file) {
    long numIssues = getNumberIssues();

    if (numIssues > THRESHOLD) {
      clearFile(file);
    }
  }

  public void store(VirtualFile file, Collection<StoredIssue> issues) {
    // this will also delete all existing issues in the file
    storePerFile.put(file, issues);
  }

  public static class StoredIssue {
    private RangeMarker range;
    private Issue issue;
    private PsiFile psiFile;

    public StoredIssue(Issue issue, PsiFile psiFile) {
      this.issue = issue;
      this.psiFile = psiFile;
    }

    public StoredIssue(Issue issue, PsiFile psiFile, RangeMarker range) {
      this.range = range;
      this.issue = issue;
      this.psiFile = psiFile;
    }

    public Issue issue() {
      return issue;
    }

    @CheckForNull
    public RangeMarker range() {
      return range;
    }

    public PsiFile psiFile() {
      return psiFile;
    }
  }
}
