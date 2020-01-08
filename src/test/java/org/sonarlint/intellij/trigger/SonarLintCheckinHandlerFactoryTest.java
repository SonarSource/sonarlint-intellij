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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarLintCheckinHandlerFactoryTest {
  private SonarLintCheckinHandlerFactory sonarLintCheckinHandlerFactory;

  private CheckinProjectPanel panel = mock(CheckinProjectPanel.class);
  private VirtualFile file = mock(VirtualFile.class);
  private Project project = mock(Project.class);

  @Before
  public void setUp() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    when(panel.getVirtualFiles()).thenReturn(Collections.singletonList(file));
    when(panel.getProject()).thenReturn(project);

    sonarLintCheckinHandlerFactory = new SonarLintCheckinHandlerFactory(settings);
  }

  @Test
  public void testFactory() {
    CheckinHandler handler = sonarLintCheckinHandlerFactory.createHandler(panel, new CommitContext());
    assertThat(handler).isInstanceOf(SonarLintCheckinHandler.class);

    verify(panel).getProject();
  }

}
