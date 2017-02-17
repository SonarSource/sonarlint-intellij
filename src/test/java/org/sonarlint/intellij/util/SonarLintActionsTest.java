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
package org.sonarlint.intellij.util;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

public class SonarLintActionsTest extends SonarTest {
  @Before
  public void prepare() {
    super.register(app, SonarLintActions.class, new SonarLintActions());
  }

  @Test
  public void testActions() {
    super.register(app, ActionManager.class, mock(ActionManager.class, RETURNS_DEEP_STUBS));

    SonarLintActions instance = SonarLintActions.getInstance();
    instance.initComponent();
    assertActionFields(instance.analyzeAllFiles());
    assertActionFields(instance.clearResults());
    assertAction(instance.configure());
    assertAction(instance.cancelAnalysis());
    assertActionFields(instance.analyzeChangedFiles());
    assertActionFields(instance.analyzeCurrentFile());
    assertActionFields(instance.clearResults());
    assertActionFields(instance.clearIssues());
    assertActionFields(instance.cleanConsole());

    assertThat(instance.getComponentName()).isEqualTo("SonarLintActions");
  }

  private void assertAction(AnAction action) {
    assertThat(action).isNotNull();
  }

  private void assertActionFields(AnAction action) {
    assertThat(action).isNotNull();
    assertThat(action.getTemplatePresentation().getText()).isNotNull();
    assertThat(action.getTemplatePresentation().getIcon()).isNotNull();
    assertThat(action.getTemplatePresentation().getDescription()).isNotNull();
  }
}
