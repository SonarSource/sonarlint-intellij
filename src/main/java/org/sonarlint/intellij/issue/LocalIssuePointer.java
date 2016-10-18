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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

public class LocalIssuePointer implements IssuePointer {
  private static final AtomicLong UID_GEN = new AtomicLong();
  private final long uid;
  private final RangeMarker range;
  private final PsiFile psiFile;
  private Integer checksum;

  // tracked fields (mutable)
  private Long creationDate;
  private String serverIssueKey;
  private boolean resolved;
  private String assignee;
  private String severity;
  private String ruleKey;
  private String ruleName;
  private String message;

  public LocalIssuePointer(Issue issue, PsiFile psiFile) {
    this(issue, psiFile, null);
  }

  public LocalIssuePointer(Issue issue, PsiFile psiFile, @Nullable RangeMarker range) {
    this.range = range;
    this.message = issue.getMessage();
    this.ruleKey = issue.getRuleKey();
    this.ruleName = issue.getRuleName();
    this.severity = issue.getSeverity();
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
    if (!psiFile.isValid()) {
      return false;
    }

    return range == null || range.isValid();
  }

  @Override
  public Integer getLine() {
    if (range != null && isValid()) {
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
    return message;
  }

  @Override
  public Integer getLineHash() {
    return checksum;
  }

  public void setLineHash(Integer hash) {
    this.checksum = hash;
  }

  @Override
  public String getRuleKey() {
    return ruleKey;
  }

  public long uid() {
    return uid;
  }

  @CheckForNull
  public RangeMarker range() {
    return range;
  }

  public PsiFile psiFile() {
    return psiFile;
  }

  public String severity() {
    return severity;
  }

  public void severity(String severity) {
    this.severity = severity;
  }

  public void ruleKey(String ruleKey) {
    this.ruleKey = ruleKey;
  }

  public void ruleName(String ruleName) {
    this.ruleName = ruleName;
  }

  public String ruleName() {
    return ruleName;
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
