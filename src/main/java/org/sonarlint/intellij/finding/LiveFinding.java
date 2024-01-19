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
package org.sonarlint.intellij.finding;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
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
  private final Module module;
  private final RangeMarker range;
  private final VirtualFile virtualFile;
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
  private boolean isOnNewCode;
  private SoftwareQuality highestQuality = null;
  private ImpactSeverity highestImpact = null;

  protected LiveFinding(Module module, Issue issue, VirtualFile virtualFile, @Nullable RangeMarker range, @Nullable FindingContext context,
                        List<QuickFix> quickFixes) {
    this.module = module;
    this.range = range;
    this.message = issue.getMessage();
    this.ruleKey = issue.getRuleKey();
    this.severity = issue.getSeverity();
    this.virtualFile = virtualFile;
    this.uid = UID_GEN.getAndIncrement();
    this.context = context;
    this.quickFixes = quickFixes;
    this.ruleDescriptionContextKey = issue.getRuleDescriptionContextKey().orElse(null);

    this.cleanCodeAttribute = issue.getCleanCodeAttribute().orElse(null);
    this.impacts = issue.getImpacts();
    var highestQualityImpact = getImpacts().entrySet().stream().max(Map.Entry.comparingByValue());
    this.highestQuality = highestQualityImpact.map(Map.Entry::getKey).orElse(null);
    this.highestImpact = highestQualityImpact.map(Map.Entry::getValue).orElse(null);

    if (range != null) {
      var document = computeReadActionSafely(virtualFile, range::getDocument);
      if (document != null) {
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
    } else {
      this.textRangeHash = null;
      this.textRangeHashString = null;
      this.lineHash = null;
      this.lineHashString = null;
    }
  }

  @Override
  public UUID getId() {
    return getBackendId();
  }

  public void setBackendId(UUID backendId) {
    this.backendId = backendId;
  }

  public Module getModule() {
    return module;
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
    if (Boolean.TRUE.equals(computeReadActionSafely(virtualFile, virtualFile::isValid))) {
      return range == null || range.isValid();
    } else {
      return false;
    }
  }

  @Override
  public Integer getLine() {
    if (range != null) {
      return computeReadActionSafely(virtualFile, () -> range.getDocument().getLineNumber(range.getStartOffset()) + 1);
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
    return toValidTextRange(range);
  }

  @CheckForNull
  public static TextRange toValidTextRange(@Nullable RangeMarker rangeMarker) {
    if (rangeMarker == null) {
      return new TextRange(0, 0);
    }
    if (rangeMarker.isValid()) {
      var startOffset = rangeMarker.getStartOffset();
      var endOffset = rangeMarker.getEndOffset();
      if (startOffset < endOffset && startOffset >= 0) {
        var document = rangeMarker.getDocument();
        if (DocumentUtil.isValidOffset(startOffset, document) && DocumentUtil.isValidOffset(endOffset, document)) {
          return new TextRange(startOffset, endOffset);
        }
      }
    }
    return null;
  }

  @NotNull
  public VirtualFile file() {
    return virtualFile;
  }

  @NotNull
  public Project project() {
    return module.getProject();
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

  public void setOnNewCode(boolean onNewCode) {
    isOnNewCode = onNewCode;
  }

  @Override
  public boolean isOnNewCode() {
    return isOnNewCode;
  }

  @Override
  public SoftwareQuality getHighestQuality() {
    return highestQuality;
  }

  @Override
  public ImpactSeverity getHighestImpact() {
    return highestImpact;
  }

}
