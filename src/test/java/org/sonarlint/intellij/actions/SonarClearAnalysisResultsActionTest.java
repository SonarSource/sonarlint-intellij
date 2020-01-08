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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.issue.AnalysisResultIssues;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarClearAnalysisResultsActionTest extends SonarTest {
  private SonarClearAnalysisResultsAction action = new SonarClearAnalysisResultsAction(null, null, null);

  @Test
  public void clear() {
    AnalysisResultIssues analysisResultIssues = mock(AnalysisResultIssues.class);
    super.register(AnalysisResultIssues.class, analysisResultIssues);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(project);
    action.actionPerformed(event);

    verify(analysisResultIssues).clear();
  }

  @Test
  public void noProject() {
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(null);
    action.actionPerformed(event);
  }
}
