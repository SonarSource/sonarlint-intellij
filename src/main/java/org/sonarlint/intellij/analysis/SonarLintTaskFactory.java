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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.issue.IssueProcessor;

public class SonarLintTaskFactory extends AbstractProjectComponent {
  private final SonarLintStatus status;
  private final IssueProcessor processor;
  private final SonarApplication sonarApplication;

  public SonarLintTaskFactory(Project project, SonarLintStatus status, IssueProcessor processor, SonarApplication sonarApplication) {
    super(project);
    this.status = status;
    this.processor = processor;
    this.sonarApplication = sonarApplication;
  }

  public SonarLintTask createTask(SonarLintJob job, boolean startInBackground) {
    return new SonarLintTask(processor, job, startInBackground, sonarApplication);
  }

  public SonarLintUserTask createUserTask(SonarLintJob job, boolean modal) {
    return new SonarLintUserTask(processor, job, status, modal, sonarApplication);
  }
}
