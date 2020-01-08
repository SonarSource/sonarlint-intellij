/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.issue.AnalysisResultIssues;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class AnalysisResults {
  private static final String LABEL = "Trigger an analysis to find issues in the project sources";
  private final Project project;
  private final AnalysisResultIssues issues;

  public AnalysisResults(Project project) {
    this.project = project;
    this.issues = SonarLintUtils.get(project, AnalysisResultIssues.class);
  }

  public String getEmptyText() {
    if (issues.wasAnalyzed()) {
      return "No issues found";
    } else {
      return "No analysis done";
    }
  }

  @CheckForNull
  public Instant getLastAnalysisDate() {
    return issues.lastAnalysisDate();
  }

  @CheckForNull
  public String whatAnalyzed() {
    return issues.whatAnalyzed();
  }

  public Map<VirtualFile, Collection<LiveIssue>> issues() {
    return issues.issues();
  }

  public String getLabelText() {
    return LABEL;
  }

}
