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
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TaskProgressMonitorTest {
  private ProgressIndicator wrapped = mock(ProgressIndicator.class);
  private TaskProgressMonitor monitor = new TaskProgressMonitor(wrapped);

  @Test
  public void should_wrap() {
    monitor.finishNonCancelableSection();
    verify(wrapped).finishNonCancelableSection();

    monitor.isCanceled();
    verify(wrapped).isCanceled();

    monitor.setFraction(0.5f);
    verify(wrapped).setFraction(0.5f);

    monitor.setIndeterminate(true);
    verify(wrapped).setIndeterminate(true);

    monitor.setMessage("message");
    verify(wrapped).setText("message");

    monitor.startNonCancelableSection();
    verify(wrapped).startNonCancelableSection();
  }
}
