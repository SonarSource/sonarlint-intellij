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

import com.intellij.openapi.project.Project;
import org.junit.Test;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.issue.IssueProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarLintTaskFactoryTest {
  private SonarLintTaskFactory sonarLintTaskFactory = new SonarLintTaskFactory(mock(Project.class),
    mock(SonarLintStatus.class), mock(IssueProcessor.class), mock(SonarApplication.class));

  @Test
  public void should_create_tasks() {
    Project project = mock(Project.class);
    SonarLintJob job = mock(SonarLintJob.class);
    when(job.project()).thenReturn(project);
    assertThat(sonarLintTaskFactory.createTask(job, false)).isNotNull();
    assertThat(sonarLintTaskFactory.createUserTask(job, false)).isNotNull();
  }
}
