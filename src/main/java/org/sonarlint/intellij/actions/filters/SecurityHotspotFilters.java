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
package org.sonarlint.intellij.actions.filters;

import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;

public enum SecurityHotspotFilters {

  SHOW_ALL("Show All"),
  LOCAL_ONLY("Local Only"),
  EXISTING_ON_SONARQUBE("Existing On SonarQube");

  public static final SecurityHotspotFilters DEFAULT_FILTER = SHOW_ALL;
  private final String title;

  SecurityHotspotFilters(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }

  public boolean shouldIncludeSecurityHotspot(LiveSecurityHotspot securityHotspot) {
    if (this == SHOW_ALL) {
      return true;
    } else if (this == LOCAL_ONLY) {
      return null == securityHotspot.getServerFindingKey();
    } else if (this == EXISTING_ON_SONARQUBE) {
      return null != securityHotspot.getServerFindingKey();
    }
    return true;
  }

}
