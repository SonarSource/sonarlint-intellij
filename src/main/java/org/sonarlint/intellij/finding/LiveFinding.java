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

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.finding.tracking.Trackable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.digest.DigestUtils.md5;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;

public abstract class LiveFinding implements Trackable, Finding {
  private static final AtomicLong UID_GEN = new AtomicLong();

  private final long uid;
  private UUID backendId;
  private final RangeMarker range;
  private final PsiFile psiFile;
  private final Integer textRangeHash;
  private final String textRangeHashString;
  private final Integer lineHash;
  private final String lineHashString;
  private final String message;
  private final String ruleKey;

  private final FindingContext context;
  private final List<QuickFix> quickFixes;
  private final String ruleDescriptionContextKey;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final Map<SoftwareQuality, ImpactSeverity> impacts;

  // tracked fields (mutable)
  private IssueSeverity severity;
  private Long introductionDate;
  private String serverFindingKey;
  private boolean resolved;


  protected LiveFinding(Issue issue, PsiFile psiFile, @Nullable RangeMarker range, @Nullable FindingContext context,
    List<QuickFix> quickFixes) {
    this.range = range;
    this.message = issue.getMessage();
    this.ruleKey = issue.getRuleKey();
    this.severity = issue.getSeverity();
    this.psiFile = psiFile;
    this.uid = UID_GEN.getAndIncrement();
    this.context = context;
    this.quickFixes = quickFixes;
    this.ruleDescriptionContextKey = issue.getRuleDescriptionContextKey().orElse(null);

    this.cleanCodeAttribute = issue.getCleanCodeAttribute().orElse(null);
    this.impacts = issue.getImpacts();

    if (range != null) {
      var document = range.getDocument();
      var lineContent = document.getText(new TextRange(range.getStartOffset(), range.getEndOffset()));
      this.textRangeHash = checksum(lineContent);
      this.textRangeHashString = md5Hex(lineContent.replaceAll("[\\s]", ""));

      var line = document.getLineNumber(range.getStartOffset());
      var lineStartOffset = document.getLineStartOffset(line);
      var lineEndOffset = document.getLineEndOffset(line);
      var rangeContent = document.getText(new TextRange(lineStartOffset, lineEndOffset));
      this.lineHash = checksum(rangeContent);
      this.lineHashString = md5Hex(rangeContent.replaceAll("[\\s]", ""));
    } else {
      this.textRangeHash = null;
      this.textRangeHashString = null;
      this.lineHash = null;
      this.lineHashString = null;
    }
  }

  @CheckForNull
  @Override
  public UUID getId() {
    return getBackendId();
  }

  public void setBackendId(UUID backendId) {
    this.backendId = backendId;
  }

  @CheckForNull
  public UUID getBackendId() {
    return backendId;
  }

  private static int checksum(String content) {
    return Hex.encodeHexString(md5(content.replaceAll("\\s", "").getBytes(UTF_8))).hashCode();
  }

  @Override
  public boolean isValid() {
    if (!psiFile.isValid()) {
      return false;
    }

    return range == null || range.isValid();
  }

  @Override
  public Integer getLine() {
    if (range != null) {
      return computeReadActionSafely(psiFile, () -> range.getDocument().getLineNumber(range.getStartOffset()) + 1);
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

  public String getTextRangeHashString() {
    return textRangeHashString;
  }

  @Override
  public Integer getLineHash() {
    return lineHash;
  }

  public String getLineHashString() {
    return lineHashString;
  }

  @NotNull
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

  public VirtualFile getFile() {
    return psiFile().getVirtualFile();
  }

  public VirtualFile file() {
    return getFile();
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
  public String getServerKey() {
    return serverFindingKey;
  }

  @Override
  public String getServerFindingKey() {
    return serverFindingKey;
  }

  @Override
  public boolean isResolved() {
    return resolved;
  }

  @Override
  public CleanCodeAttribute getCleanCodeAttribute() {
    return this.cleanCodeAttribute;
  }

  @NotNull
  @Override
  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return this.impacts;
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

  @org.jetbrains.annotations.Nullable
  @Override
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }
}
