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
package org.sonarlint.intellij.actions.filters;

import com.intellij.openapi.project.Project;
import java.util.function.Function;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public enum SecurityHotspotFilters {

  SHOW_ALL(p -> "Show All"),
  LOCAL_ONLY(p -> "Local Only"),
  EXISTING_ON_SERVER(p -> "Existing" + getService(p, ProjectBindingManager.class).tryGetServerConnection().map(c -> " on " + c.getProductName()).orElse(""));

  public static final SecurityHotspotFilters DEFAULT_FILTER = SHOW_ALL;
  private final Function<Project, String> titleSupplier;

  SecurityHotspotFilters(Function<Project, String> titleSupplier) {
    this.titleSupplier = titleSupplier;
  }

  public String getTitle(Project project) {
    return titleSupplier.apply(project);
  }

  public boolean shouldIncludeSecurityHotspot(LiveSecurityHotspot securityHotspot) {
    if (this == SHOW_ALL) {
      return true;
    } else if (this == LOCAL_ONLY) {
      return null == securityHotspot.getServerKey();
    } else if (this == EXISTING_ON_SERVER) {
      return null != securityHotspot.getServerKey();
    }
    return true;
  }
}
