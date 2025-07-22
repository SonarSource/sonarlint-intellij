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
  private final ToolWindowVerboseModeAction action = new ToolWindowVerboseModeAction();
  private AnActionEvent event;

  @BeforeEach
  void prepare() {
    event = SonarLintTestUtils.createAnActionEvent(getProject());
  }

  @Test
  void test_selected_when_project_is_null() {
    when(event.getProject()).thenReturn(null);
    
    var result = action.isSelected(event);
    assertThat(result).isFalse();
  }

  @Test
  void test_set_selected_to_true() {
    getProjectSettings().setVerboseEnabled(false);

    action.setSelected(event, true);
    
    assertThat(getProjectSettings().isVerboseEnabled()).isTrue();
  }

  @Test
  void test_set_selected_to_false() {
    getProjectSettings().setVerboseEnabled(true);

    action.setSelected(event, false);
    
    assertThat(getProjectSettings().isVerboseEnabled()).isFalse();
  }

  @Test
  void test_set_selected_when_project_is_null() {
    getProjectSettings().setVerboseEnabled(true);
    when(event.getProject()).thenReturn(null);
    
    action.setSelected(event, false);
    
    assertThat(getProjectSettings().isVerboseEnabled()).isTrue();
  }

  @Test
  void test_set_selected_multiple_times() {
    getProjectSettings().setVerboseEnabled(false);

    action.setSelected(event, true);
    assertThat(getProjectSettings().isVerboseEnabled()).isTrue();

    action.setSelected(event, false);
    assertThat(getProjectSettings().isVerboseEnabled()).isFalse();

    action.setSelected(event, true);
    assertThat(getProjectSettings().isVerboseEnabled()).isTrue();
  }
}
