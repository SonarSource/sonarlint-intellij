/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarLintCheckinHandlerFactoryTests {
  private SonarLintCheckinHandlerFactory sonarLintCheckinHandlerFactory;

  private CheckinProjectPanel panel = mock(CheckinProjectPanel.class);
  private VirtualFile file = mock(VirtualFile.class);
  private Project project = mock(Project.class);

  @BeforeEach
  void setUp() {
    when(panel.getVirtualFiles()).thenReturn(List.of(file));
    when(panel.getProject()).thenReturn(project);

    sonarLintCheckinHandlerFactory = new SonarLintCheckinHandlerFactory();
  }

  @Test
  void testFactory() {
    var handler = sonarLintCheckinHandlerFactory.createHandler(panel, new CommitContext());
    assertThat(handler).isInstanceOf(SonarLintCheckinHandler.class);

    verify(panel).getProject();
  }

}
