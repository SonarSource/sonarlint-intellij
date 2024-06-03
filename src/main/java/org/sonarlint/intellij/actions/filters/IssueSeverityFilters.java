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

import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

public enum IssueSeverityFilters {

  SHOW_ALL(p -> "Show All"),
  INFO(p -> "Info"),
  MINOR(p -> "Minor"),
  MAJOR(p -> "Major"),
  CRITICAL(p -> "Critical"),
  BLOCKER(p -> "Blocker");

  public static final IssueSeverityFilters DEFAULT_FILTER = SHOW_ALL;
  private final Function<Project, String> titleSupplier;

  IssueSeverityFilters(Function<Project, String> titleSupplier) {
    this.titleSupplier = titleSupplier;
  }

  public String getTitle(Project project) {
    return titleSupplier.apply(project);
  }

  public boolean shouldIncludeIssue(IssueSeverity severity) {
    return switch (severity) {
        case INFO ->  this.compareTo(INFO) <= 0;
        case MINOR -> this.compareTo(MINOR) <= 0;
        case MAJOR -> this.compareTo(MAJOR) <= 0;
        case CRITICAL -> this.compareTo(CRITICAL) <= 0;
        case BLOCKER -> this.compareTo(BLOCKER) <= 0;
    };
  }
}
