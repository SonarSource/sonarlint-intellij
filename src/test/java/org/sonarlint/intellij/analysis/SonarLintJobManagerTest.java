/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLintJobManagerTest extends SonarTest {
  @Mock
  private SonarLintTaskFactory factory;
  @Mock
  private SonarLintConsole console;
  @Mock
  private SonarLintStatus status;
  @Mock
  private SonarLintUserTask task;
  @Mock
  private ProgressManager progressManager;

  private SonarLintJobManager manager;

  @Before
  public void prepare() {
    MockitoAnnotations.initMocks(this);
    when(app.isDispatchThread()).thenReturn(true);
    when(task.isHeadless()).thenReturn(true);
    when(task.isConditionalModal()).thenReturn(true);
    when(status.tryRun()).thenReturn(true);
    when(factory.createTask(any(SonarLintJob.class), eq(true))).thenReturn(task);
    when(factory.createUserTask(any(SonarLintJob.class), eq(true))).thenReturn(task);

    manager = new SonarLintJobManager(project, factory, progressManager, status, console);
  }

  @Test
  public void testUserTask() {
    manager.submitManual(mock(Module.class), Collections.singleton(mock(VirtualFile.class)), TriggerType.ACTION, true);
    verify(factory).createUserTask(any(SonarLintJob.class), eq(true));
    verify(app).isDispatchThread();
    verify(progressManager).run(task);
  }

  @Test
  public void testRunBackground() {
    manager.submitBackground(mock(Module.class), Collections.singleton(mock(VirtualFile.class)), TriggerType.ACTION);
    verify(factory).createTask(any(SonarLintJob.class), eq(true));
    verify(app).isDispatchThread();
    verify(progressManager).run(task);
  }
}
