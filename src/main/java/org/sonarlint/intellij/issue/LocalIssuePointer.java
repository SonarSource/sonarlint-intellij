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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

public class LocalIssuePointer implements IssuePointer {
  private static final AtomicLong UID_GEN = new AtomicLong();
  private final long uid;
  private final RangeMarker range;
  private final Issue issue;
  private final PsiFile psiFile;
  private final Integer checksum;

  // tracked fields (mutable)
  private Long creationDate;
  private String serverIssueKey;
  private boolean resolved;
  private String assignee;

  public LocalIssuePointer(Issue issue, PsiFile psiFile) {
    this(issue, psiFile, null);
  }

  public LocalIssuePointer(Issue issue, PsiFile psiFile, @Nullable RangeMarker range) {
    this.range = range;
    this.issue = issue;
    this.psiFile = psiFile;
    this.assignee = "";
    this.uid = UID_GEN.getAndIncrement();
    if (range != null) {
      this.checksum = checksum(range.getDocument().getText(new TextRange(range.getStartOffset(), range.getEndOffset())));
    } else {
      this.checksum = null;
    }
  }

  private static int checksum(String content) {
    return content.replaceAll("[\\s]", "").hashCode();
  }

  public boolean isValid() {
    if (psiFile != null && !psiFile.isValid()) {
      return false;
    }

    return range == null || range.isValid();
  }

  @Override
  public Integer getLine() {
    if(range != null && isValid()) {
      return range.getDocument().getLineNumber(range.getStartOffset()) + 1;
    }

    return null;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }

  @Override
  public String getMessage() {
    return issue.getMessage();
  }

  @Override
  public Integer getLineHash() {
    return checksum;
  }

  @Override
  public String getRuleKey() {
    return issue.getRuleKey();
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

  @Override
  public Long getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(@Nullable Long creationDate) {
    this.creationDate = creationDate;
  }

  @Override
  public String getServerIssueKey() {
    return serverIssueKey;
  }

  public void setServerIssueKey(@Nullable String serverIssueKey) {
    this.serverIssueKey = serverIssueKey;
  }

  @Override
  public boolean isResolved() {
    return resolved;
  }

  public void setResolved(boolean resolved) {
    this.resolved = resolved;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }
}
