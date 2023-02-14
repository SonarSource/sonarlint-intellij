/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.finding;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Hex;
import org.sonarlint.intellij.finding.tracking.Trackable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.digest.DigestUtils.md5;

public abstract class LiveFinding implements Trackable, Finding {
  private static final AtomicLong UID_GEN = new AtomicLong();

  private final long uid;
  private final RangeMarker range;
  private final PsiFile psiFile;
  private final Integer textRangeHash;
  private final Integer lineHash;
  private final String message;
  private final String ruleKey;
  private final FindingContext context;
  private final List<QuickFix> quickFixes;

  // tracked fields (mutable)
  private IssueSeverity severity;
  private Long introductionDate;
  private String serverFindingKey;
  private boolean resolved;

  protected LiveFinding(Issue issue, PsiFile psiFile, @Nullable RangeMarker range, @Nullable FindingContext context, List<QuickFix> quickFixes) {
    this.range = range;
    this.message = issue.getMessage();
    this.ruleKey = issue.getRuleKey();
    this.severity = issue.getSeverity();
    this.psiFile = psiFile;
    this.uid = UID_GEN.getAndIncrement();
    this.context = context;
    this.quickFixes = quickFixes;

    if (range != null) {
      var document = range.getDocument();
      this.textRangeHash = checksum(document.getText(new TextRange(range.getStartOffset(), range.getEndOffset())));

      var line = range.getDocument().getLineNumber(range.getStartOffset());
      var lineStartOffset = document.getLineStartOffset(line);
      var lineEndOffset = document.getLineEndOffset(line);
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
    if (range != null) {
      return ReadAction.compute(() -> isValid() ? (range.getDocument().getLineNumber(range.getStartOffset()) + 1) : null);
    }

    return null;
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

  @CheckForNull
  public TextRange getValidTextRange() {
    return toValidTextRange(psiFile, range);
  }

  @CheckForNull
  public static TextRange toValidTextRange(@Nullable PsiFile psiFile, @Nullable RangeMarker rangeMarker) {
    if (psiFile == null || !psiFile.isValid()) {
      return null;
    }
    if (rangeMarker == null) {
      return psiFile.getTextRange();
    }
    if (rangeMarker.isValid()) {
      var startOffset = rangeMarker.getStartOffset();
      var endOffset = rangeMarker.getEndOffset();
      if (startOffset < endOffset && startOffset >= 0) {
        var textRange = new TextRange(startOffset, endOffset);
        if (psiFile.getTextRange().contains(textRange)) {
          return textRange;
        }
      }
    }
    return null;
  }

  public PsiFile psiFile() {
    return psiFile;
  }

  @Override
  public IssueSeverity getUserSeverity() {
    return severity;
  }

  @Override
  public Long getIntroductionDate() {
    return introductionDate;
  }

  @Override
  public String getServerFindingKey() {
    return serverFindingKey;
  }

  @Override
  public boolean isResolved() {
    return resolved;
  }

  // mutable fields
  public void setServerFindingKey(@Nullable String serverHotspotKey) {
    this.serverFindingKey = serverHotspotKey;
  }

  public void setIntroductionDate(@Nullable Long introductionDate) {
    this.introductionDate = introductionDate;
  }

  public void setResolved(boolean resolved) {
    this.resolved = resolved;
  }

  public void setSeverity(IssueSeverity severity) {
    this.severity = severity;
  }

  public Optional<FindingContext> context() {
    return Optional.ofNullable(context);
  }

  public List<QuickFix> quickFixes() {
    return quickFixes;
  }
}
