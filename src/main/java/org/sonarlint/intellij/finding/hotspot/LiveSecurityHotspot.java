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
package org.sonarlint.intellij.finding.hotspot;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.finding.FindingContext;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.QuickFix;
import org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

public class LiveSecurityHotspot extends LiveFinding {
  private final VulnerabilityProbability vulnerabilityProbability;
  private HotspotReviewStatus status;

  public LiveSecurityHotspot(Module module, RaisedHotspotDto hotspot, VirtualFile virtualFile, List<QuickFix> quickFixes) {
    this(module, hotspot, virtualFile, null, null, quickFixes);
  }

  public LiveSecurityHotspot(Module module, RaisedHotspotDto hotspot, VirtualFile virtualFile, @Nullable RangeMarker range, @Nullable FindingContext context,
    List<QuickFix> quickFixes) {
    super(module, hotspot, virtualFile, range, context, quickFixes);
    this.vulnerabilityProbability = hotspot.getVulnerabilityProbability();
    this.status = mapStatus(hotspot.getStatus());
  }

  private static HotspotReviewStatus mapStatus(@Nullable HotspotStatus status) {
    if (status == null) {
      return HotspotReviewStatus.TO_REVIEW;
    }
    return switch (status) {
      case TO_REVIEW -> HotspotReviewStatus.TO_REVIEW;
      case SAFE -> HotspotReviewStatus.SAFE;
      case FIXED -> HotspotReviewStatus.FIXED;
      case ACKNOWLEDGED -> HotspotReviewStatus.ACKNOWLEDGED;
    };
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  @Override
  public CleanCodeAttribute getCleanCodeAttribute() {
    return null;
  }

  @NotNull
  @Override
  public List<ImpactDto> getImpacts() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public RuleType getType() {
    return RuleType.SECURITY_HOTSPOT;
  }

  public void setStatus(HotspotReviewStatus status) {
    this.status = status;
  }

  public void setStatus(HotspotStatus status) {
    this.status = HotspotReviewStatus.valueOf(status.name());
  }

  public HotspotReviewStatus getStatus() {
    return status;
  }

  @Override
  public boolean isResolved() {
    return status.isResolved();
  }
}
