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

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.PsiFile;
import static org.sonarsource.sonarlint.core.IssueListener.Issue;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;

public class IssuePointer {
  private static final AtomicLong UID_GEN = new AtomicLong();
  private long uid;
  private RangeMarker range;
  private Issue issue;
  private PsiFile psiFile;

  public IssuePointer(Issue issue, PsiFile psiFile) {
    this(issue, psiFile, null);
  }

  public IssuePointer(Issue issue, PsiFile psiFile, @Nullable RangeMarker range) {
    this.range = range;
    this.issue = issue;
    this.psiFile = psiFile;
    this.uid = UID_GEN.getAndIncrement();
  }

  public boolean isValid() {
    if (!psiFile.isValid()) {
      return false;
    }

    if (range != null) {
      return range.isValid();
    }

    return true;
  }

  public long uid() {
    return uid;
  }

  @Nonnull
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
