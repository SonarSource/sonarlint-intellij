/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.client.utils.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;

public abstract class LiveFinding implements Finding {
  private static final AtomicLong UID_GEN = new AtomicLong();

  private final long uid;
  private final UUID backendId;
  private final Module module;
  private final RangeMarker range;
  private final VirtualFile virtualFile;
  private final String message;
  private final String ruleKey;
  private final boolean isOnNewCode;
  private final FindingContext context;
  private final List<QuickFix> quickFixes;
  private final String ruleDescriptionContextKey;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final List<ImpactDto> impacts;
  private final IssueSeverity severity;
  private final SoftwareQuality highestQuality;
  private final ImpactSeverity highestImpact;

  private Instant introductionDate;
  private String serverFindingKey;
  private boolean resolved;

  protected LiveFinding(Module module, RaisedFindingDto finding, VirtualFile virtualFile, @Nullable RangeMarker range, @Nullable FindingContext context,
    List<QuickFix> quickFixes) {
    this.backendId = finding.getId();
    this.serverFindingKey = finding.getServerKey();
    this.module = module;
    this.range = range;
    this.message = finding.getPrimaryMessage();
    this.ruleKey = finding.getRuleKey();
    this.virtualFile = virtualFile;
    this.uid = UID_GEN.getAndIncrement();
    this.context = context;
    this.quickFixes = quickFixes;
    this.ruleDescriptionContextKey = finding.getRuleDescriptionContextKey();
    this.introductionDate = finding.getIntroductionDate();
    this.isOnNewCode = finding.isOnNewCode();
    this.resolved = finding.isResolved();

    if (finding.getSeverityMode().isLeft()) {
      this.severity = finding.getSeverityMode().getLeft().getSeverity();
      this.cleanCodeAttribute = null;
      this.impacts = Collections.emptyList();
      this.highestQuality = null;
      this.highestImpact = null;
    } else {
      this.severity = null;
      this.cleanCodeAttribute = CleanCodeAttribute.fromDto(finding.getSeverityMode().getRight().getCleanCodeAttribute());
      this.impacts = finding.getSeverityMode().getRight().getImpacts();
      // Is empty for Security Hotspots
      if (!impacts.isEmpty()) {
        var highestQualityImpact = Collections.max(impacts, Comparator.comparing(ImpactDto::getImpactSeverity));
        this.highestQuality = SoftwareQuality.fromDto(highestQualityImpact.getSoftwareQuality());
        this.highestImpact = ImpactSeverity.fromDto(highestQualityImpact.getImpactSeverity());
      } else {
        this.highestQuality = null;
        this.highestImpact = null;
      }
    }
  }

  @NotNull
  public UUID getId() {
    return getBackendId();
  }

  public Module getModule() {
    return module;
  }

  public UUID getBackendId() {
    return backendId;
  }

  @Override
  public boolean isValid() {
    if (Boolean.TRUE.equals(computeReadActionSafely(virtualFile, virtualFile::isValid))) {
      return range == null || range.isValid();
    } else {
      return false;
    }
  }

  public String getMessage() {
    return message;
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

  @Nullable
  public IssueSeverity getUserSeverity() {
    return severity;
  }

  public Instant getIntroductionDate() {
    return introductionDate;
  }

  @Override
  public String getServerKey() {
    return serverFindingKey;
  }

  @Override
  public boolean isResolved() {
    return resolved;
  }

  @Nullable
  @Override
  public CleanCodeAttribute getCleanCodeAttribute() {
    return this.cleanCodeAttribute;
  }

  @NotNull
  @Override
  public List<ImpactDto> getImpacts() {
    return this.impacts;
  }

  // mutable fields
  public void setServerFindingKey(@Nullable String serverHotspotKey) {
    this.serverFindingKey = serverHotspotKey;
  }

  public void setIntroductionDate(@Nullable Instant introductionDate) {
    this.introductionDate = introductionDate;
  }

  public void setResolved(boolean resolved) {
    this.resolved = resolved;
  }

  public Optional<FindingContext> context() {
    return Optional.ofNullable(context);
  }

  public List<QuickFix> quickFixes() {
    return quickFixes;
  }

  @Nullable
  @Override
  public String getRuleDescriptionContextKey() {
    return ruleDescriptionContextKey;
  }

  public boolean isOnNewCode() {
    return isOnNewCode;
  }

  @Nullable
  @Override
  public SoftwareQuality getHighestQuality() {
    return highestQuality;
  }

  @Nullable
  @Override
  public ImpactSeverity getHighestImpact() {
    return highestImpact;
  }
}
