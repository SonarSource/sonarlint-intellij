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
package org.sonarlint.intellij.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskProgressMonitorTests {
  private final ProgressIndicator wrapped = mock(ProgressIndicator.class);

  @Test
  void cancel_if_project_disposed() {
    var project = mock(Project.class);
    var monitor = new TaskProgressMonitor(wrapped, project, () -> false);

    when(project.isDisposed()).thenReturn(false);
    assertThat(monitor.isCanceled()).isFalse();

    when(project.isDisposed()).thenReturn(true);
    assertThat(monitor.isCanceled()).isTrue();
  }

  @Test
  void cancel_if_flag_set() {
    var monitor = new TaskProgressMonitor(wrapped, null, () -> true);

    assertThat(monitor.isCanceled()).isTrue();
  }
}
