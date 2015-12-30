/**
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
package org.sonarlint.intellij.actions;

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.ui.SonarLintConsole;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarCleanConsoleTest extends LightPlatformCodeInsightFixtureTestCase {
  private SonarCleanConsole cleanConsole;
  private SonarLintConsole sonarLintConsole;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    cleanConsole = new SonarCleanConsole();
    sonarLintConsole = SonarLintConsole.getSonarQubeConsole(getProject());

    // make sure it's initialized
    sonarLintConsole.getConsoleView().getComponent();
  }

  @After
  public void tearDown() throws Exception {
    sonarLintConsole.getConsoleView().dispose();
    super.tearDown();
  }

  @Test
  public void testClean() {
    sonarLintConsole.info("some text");

    assertThat(sonarLintConsole.getConsoleView().getContentSize()).isNotZero();
    cleanConsole.actionPerformed(SonarLintTestUtils.createAnActionEvent(getProject()));
    assertThat(sonarLintConsole.getConsoleView().getContentSize()).isZero();
  }
}
