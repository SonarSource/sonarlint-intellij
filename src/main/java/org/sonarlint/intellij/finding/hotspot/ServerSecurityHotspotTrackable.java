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
package org.sonarlint.intellij.finding.hotspot;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.finding.tracking.Trackable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;

public class ServerSecurityHotspotTrackable implements Trackable {

  private final ServerHotspot serverHotspot;

  public ServerSecurityHotspotTrackable(ServerHotspot serverHotspot) {
    this.serverHotspot = serverHotspot;
  }

  @CheckForNull
  @Override
  public Integer getLine() {
    return serverHotspot.getTextRange().getStartLine();
  }

  @Override
  public String getMessage() {
    return serverHotspot.getMessage();
  }

  @CheckForNull
  @Override
  public Integer getTextRangeHash() {
    return null;
  }

  @CheckForNull
  @Override
  public Integer getLineHash() {
    return null;
  }

  @Override
  public String getRuleKey() {
    return serverHotspot.getRuleKey();
  }

  @CheckForNull
  @Override
  public String getServerFindingKey() {
    return serverHotspot.getKey();
  }

  @CheckForNull
  @Override
  public Long getIntroductionDate() {
    return serverHotspot.getCreationDate().toEpochMilli();
  }

  @Override
  public boolean isResolved() {
    return serverHotspot.isResolved();
  }

  @Override
  public IssueSeverity getUserSeverity() {
    return null;
  }

  @Nullable
  @Override
  public RuleType getType() {
    return RuleType.SECURITY_HOTSPOT;
  }
}
