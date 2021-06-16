/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.trigger.TriggerType;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SonarLintJobManagerTest extends LightPlatformCodeInsightFixture4TestCase {
  private SonarLintJobManager manager;
  private final TaskListener taskListener = mock(TaskListener.class);
  final AnalysisCallback analysisCallback = mock(AnalysisCallback.class);

  @Before
  public void prepare() {
    getProject().getMessageBus().connect().subscribe(TaskListener.SONARLINT_TASK_TOPIC, taskListener);
    manager = new SonarLintJobManager(getProject());
  }

  @Test
  public void testUserTask() {
    manager.submitManual(singletonList(mock(VirtualFile.class)), TriggerType.ACTION, true, analysisCallback);

    verify(analysisCallback).onSuccess(any());
  }

  @Test
  public void testRunBackground() {
    manager.submitBackground(singletonList(mock(VirtualFile.class)), TriggerType.ACTION, analysisCallback);

    verify(analysisCallback).onSuccess(any());
  }
}
