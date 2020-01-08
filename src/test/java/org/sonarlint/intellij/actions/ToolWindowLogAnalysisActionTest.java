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
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ToolWindowLogAnalysisActionTest extends SonarTest {
  private ToolWindowLogAnalysisAction action = new ToolWindowLogAnalysisAction();
  private SonarLintProjectSettings settings = new SonarLintProjectSettings();
  private AnActionEvent event = SonarLintTestUtils.createAnActionEvent(project);

  @Before
  public void prepare() {
    super.register(SonarLintProjectSettings.class, settings);
  }

  @Test
  public void testSelected() {
    settings.setAnalysisLogsEnabled(true);
    assertThat(action.isSelected(event)).isTrue();

    settings.setAnalysisLogsEnabled(false);
    assertThat(action.isSelected(event)).isFalse();

    when(event.getProject()).thenReturn(null);
    assertThat(action.isSelected(event)).isFalse();
  }

  @Test
  public void testSetSelected() {
    settings.setAnalysisLogsEnabled(true);

    action.setSelected(event, false);
    assertThat(settings.isAnalysisLogsEnabled()).isFalse();

    action.setSelected(event, true);
    assertThat(settings.isAnalysisLogsEnabled()).isTrue();

    // do nothing if there is no project
    when(event.getProject()).thenReturn(null);
    action.setSelected(event, false);
    assertThat(settings.isAnalysisLogsEnabled()).isTrue();
  }
}
