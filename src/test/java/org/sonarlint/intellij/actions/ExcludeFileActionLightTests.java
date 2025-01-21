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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_POPUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcludeFileActionLightTests extends AbstractSonarLintLightTests {
  private final ExcludeFileAction action = new ExcludeFileAction();
  private final AnActionEvent e = mock(AnActionEvent.class);
  private final Presentation presentation = new Presentation();



  @BeforeEach
  void setup() {
    when(e.getProject()).thenReturn(getProject());
    when(e.getPresentation()).thenReturn(presentation);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {myFixture.copyFileToProject("foo.php", "foo.php")});
    when(e.getPlace()).thenReturn(EDITOR_POPUP);
  }

  @Test
  void add_exclusion() {
    action.actionPerformed(e);

    await().untilAsserted(() -> assertThat(getProjectSettings().getFileExclusions()).containsOnly("FILE:foo.php"));
  }

  @Test
  void dont_add_exclusion_if_already_exists() {
    getProjectSettings().setFileExclusions(List.of("FILE:foo.php"));

    action.actionPerformed(e);

    assertThat(getProjectSettings().getFileExclusions()).containsOnly("FILE:foo.php");
  }

  @Test
  void reject_project() {
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {getProject().getBaseDir()});

    action.actionPerformed(e);
    assertThat(getProjectSettings().getFileExclusions()).isEmpty();
  }

  @Test
  void enable_action() {
    action.update(e);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isTrue();
  }
}
