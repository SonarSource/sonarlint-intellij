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
import org.sonarlint.intellij.SonarLintTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ToolWindowVerboseModeActionTests extends AbstractSonarLintLightTests {
  private ToolWindowVerboseModeAction action = new ToolWindowVerboseModeAction();
  private AnActionEvent event;

  @BeforeEach
  void prepare() {
    event = SonarLintTestUtils.createAnActionEvent(getProject());
  }

  @Test
  void testSelected() {
    getProjectSettings().setVerboseEnabled(true);
    assertThat(action.isSelected(event)).isTrue();

    getProjectSettings().setVerboseEnabled(false);
    assertThat(action.isSelected(event)).isFalse();

    when(event.getProject()).thenReturn(null);
    assertThat(action.isSelected(event)).isFalse();
  }

  @Test
  void testSetSelected() {
    getProjectSettings().setVerboseEnabled(true);

    action.setSelected(event, false);
    assertThat(getProjectSettings().isVerboseEnabled()).isFalse();

    action.setSelected(event, true);
    assertThat(getProjectSettings().isVerboseEnabled()).isTrue();

    // do nothing if there is no project
    when(event.getProject()).thenReturn(null);
    action.setSelected(event, false);
    assertThat(getProjectSettings().isVerboseEnabled()).isTrue();
  }
}
