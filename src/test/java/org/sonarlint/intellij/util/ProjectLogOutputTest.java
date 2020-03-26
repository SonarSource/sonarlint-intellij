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
package org.sonarlint.intellij.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class ProjectLogOutputTest extends SonarTest {
  private SonarLintProjectSettings settings = new SonarLintProjectSettings();
  private SonarLintConsole mockConsole = mock(SonarLintConsole.class);
  private ProjectLogOutput logOutput = new ProjectLogOutput(project, mockConsole, settings);

  @Before
  public void enableAnalysisLogs() {
    settings.setAnalysisLogsEnabled(true);
  }

  @After
  public void after() {
    verifyNoMoreInteractions(mockConsole);
  }

  @Test
  public void testDebug() {
    logOutput.log("test", LogOutput.Level.DEBUG);
    verify(mockConsole).debug("test");
  }

  @Test
  public void testNoLogAnalysis() {
    settings.setAnalysisLogsEnabled(false);
    logOutput.log("test", LogOutput.Level.INFO);
    verifyZeroInteractions(mockConsole);
  }

  @Test
  public void testInfo() {
    logOutput.log("test", LogOutput.Level.INFO);
    verify(mockConsole).info("test");
  }

  @Test
  public void testError() {
    logOutput.log("test", LogOutput.Level.ERROR);
    verify(mockConsole).error("test");
  }

  @Test
  public void testWarn() {
    logOutput.log("test", LogOutput.Level.WARN);
    verify(mockConsole).info("test");
  }

  @Test
  public void testTrace() {
    logOutput.log("test", LogOutput.Level.TRACE);
    verify(mockConsole).debug("test");
  }

  @Test
  public void testNodeCommandException() {
    logOutput.log("org.sonarsource.nodejs.NodeCommandException: Node not found :(", LogOutput.Level.DEBUG);
    verify(mockConsole).info("org.sonarsource.nodejs.NodeCommandException: Node not found :(");
  }
}
