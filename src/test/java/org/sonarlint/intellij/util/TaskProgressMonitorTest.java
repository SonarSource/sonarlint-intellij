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
package org.sonarlint.intellij.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskProgressMonitorTest {
  private ProgressIndicator wrapped = mock(ProgressIndicator.class);
  private ProgressManager progressManager = mock(ProgressManager.class);
  private TaskProgressMonitor monitor = new TaskProgressMonitor(wrapped, progressManager, null);

  @Test
  public void should_wrap() {
    assertThat(monitor.isCanceled()).isFalse();
    verify(wrapped).isCanceled();

    monitor.setFraction(0.5f);
    verify(wrapped).setFraction(0.5f);

    monitor.setIndeterminate(true);
    verify(wrapped).setIndeterminate(true);

    monitor.setMessage("message");
    verify(wrapped).setText("message");

    Runnable mockRunnable = mock(Runnable.class);
    monitor.executeNonCancelableSection(mockRunnable);
    verify(progressManager).executeNonCancelableSection(mockRunnable);
  }

  @Test
  public void cancel_if_project_disposed() {
    Project project = mock(Project.class);
    TaskProgressMonitor monitor = new TaskProgressMonitor(wrapped, project);

    when(project.isDisposed()).thenReturn(false);
    assertThat(monitor.isCanceled()).isFalse();

    when(project.isDisposed()).thenReturn(true);
    assertThat(monitor.isCanceled()).isTrue();
  }
}
