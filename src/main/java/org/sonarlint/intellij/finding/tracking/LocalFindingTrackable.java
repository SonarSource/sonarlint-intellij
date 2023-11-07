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
package org.sonarlint.intellij.finding.tracking;

import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.proto.Sonarlint;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

public class LocalFindingTrackable implements Trackable {
  private final Sonarlint.Findings.Finding finding;

  public LocalFindingTrackable(Sonarlint.Findings.Finding finding) {
    this.finding = finding;
  }

  @CheckForNull
  @Override
  public UUID getId() {
    try {
      return UUID.fromString(finding.getId());
    } catch (Exception e) {
      return null;
    }
  }

  @CheckForNull
  @Override public Integer getLine() {
    return finding.getLine() != 0 ? finding.getLine() : null;
  }

  @Override public String getMessage() {
    return finding.getMessage();
  }

  @CheckForNull
  @Override public Integer getTextRangeHash() {
    return null;
  }

  @CheckForNull
  @Override public Integer getLineHash() {
    return finding.getChecksum();
  }

  @Override public String getRuleKey() {
    return finding.getRuleKey();
  }

  @CheckForNull
  @Override public String getServerFindingKey() {
    return !finding.getServerFindingKey().isEmpty() ? finding.getServerFindingKey() : null;
  }

  @CheckForNull
  @Override public Long getIntroductionDate() {
    return finding.getIntroductionDate() != 0 ? finding.getIntroductionDate() : null;
  }

  @Override public boolean isResolved() {
    return finding.getResolved();
  }

  @Override public IssueSeverity getUserSeverity() {
    throw new UnsupportedOperationException();
  }

  @Nullable @Override public RuleType getType() {
    throw new UnsupportedOperationException();
  }
}
