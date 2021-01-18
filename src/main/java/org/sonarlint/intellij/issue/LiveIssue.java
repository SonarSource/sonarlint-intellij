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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Hex;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.digest.DigestUtils.md5;

public class LiveIssue implements Trackable {
  private static final AtomicLong UID_GEN = new AtomicLong();

  private final long uid;
  private final RangeMarker range;
  private final PsiFile psiFile;
  private final Integer textRangeHash;
  private final Integer lineHash;
  private final String ruleName;
  private final String message;
  private final String ruleKey;
  private final IssueContext context;

  // tracked fields (mutable)
  private String severity;
  private String type;
  private Long creationDate;
  private String serverIssueKey;
  private boolean resolved;
  private String assignee;

  public LiveIssue(Issue issue, PsiFile psiFile) {
    this(issue, psiFile, null, null);
  }

  public LiveIssue(Issue issue, PsiFile psiFile, @Nullable RangeMarker range, @Nullable IssueContext context) {
    this.range = range;
    this.message = issue.getMessage();
    this.ruleKey = issue.getRuleKey();
    this.ruleName = issue.getRuleName();
    this.severity = issue.getSeverity();
    this.type = issue.getType();
    this.psiFile = psiFile;
    this.assignee = "";
    this.uid = UID_GEN.getAndIncrement();
    this.context = context;

    if (range != null) {
      Document document = range.getDocument();
      this.textRangeHash = checksum(document.getText(new TextRange(range.getStartOffset(), range.getEndOffset())));

      int line = range.getDocument().getLineNumber(range.getStartOffset());
      int lineStartOffset = document.getLineStartOffset(line);
      int lineEndOffset = document.getLineEndOffset(line);
      this.lineHash = checksum(document.getText(new TextRange(lineStartOffset, lineEndOffset)));
    } else {
      this.textRangeHash = null;
      this.lineHash = null;
    }
  }

  private static int checksum(String content) {
    return Hex.encodeHexString(md5(content.replaceAll("[\\s]", "").getBytes(UTF_8))).hashCode();
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
      return ApplicationManager.getApplication().<Integer>runReadAction(() -> range.getDocument().getLineNumber(range.getStartOffset()) + 1);
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
  public Integer getTextRangeHash() {
    return textRangeHash;
  }

  @Override
  public Integer getLineHash() {
    return lineHash;
  }

  @Override
  public String getRuleKey() {
    return ruleKey;
  }

  public long uid() {
    return uid;
  }

  @CheckForNull
  public RangeMarker getRange() {
    return range;
  }

  public PsiFile psiFile() {
    return psiFile;
  }

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String getType() {
    return type;
  }

  public String getRuleName() {
    return ruleName;
  }

  @Override
  public Long getCreationDate() {
    return creationDate;
  }

  @Override
  public String getServerIssueKey() {
    return serverIssueKey;
  }

  @Override
  public boolean isResolved() {
    return resolved;
  }

  // mutable fields
  public void setServerIssueKey(@Nullable String serverIssueKey) {
    this.serverIssueKey = serverIssueKey;
  }

  public void setCreationDate(@Nullable Long creationDate) {
    this.creationDate = creationDate;
  }

  public void setResolved(boolean resolved) {
    this.resolved = resolved;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public void setType(@Nullable String type) {
    this.type = type;
  }

  public Optional<IssueContext> context() {
    return Optional.ofNullable(context);
  }
}
