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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClearReportActionTests extends AbstractSonarLintLightTests {
  private ClearReportAction action = new ClearReportAction(null, null, null);
  private SonarLintToolWindow toolWindow;

  @BeforeEach
  void prepare() {
    toolWindow = mock(SonarLintToolWindow.class);
    replaceProjectService(SonarLintToolWindow.class, toolWindow);
  }

  @Test
  void clear() {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    action.actionPerformed(event);

    verify(toolWindow).clearReportTab();
  }

  @Test
  void noProject() {
    var event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(null);

    action.actionPerformed(event);

    verify(toolWindow, never()).clearReportTab();
  }
}
