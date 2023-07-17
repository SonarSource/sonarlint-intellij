/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProjectLogOutputTests extends AbstractSonarLintLightTests {
  private final SonarLintConsole mockConsole = mock(SonarLintConsole.class);
  private ProjectLogOutput logOutput;

  @BeforeEach
  void prepare() {
    replaceProjectService(SonarLintConsole.class, mockConsole);
    getProjectSettings().setAnalysisLogsEnabled(true);
    logOutput = new ProjectLogOutput(getProject());
  }

  @Test
  void testDebug() {
    logOutput.log("test", ClientLogOutput.Level.DEBUG);
    verify(mockConsole).debug("test");
  }

  @Test
  void testNoLogAnalysis() {
    getProjectSettings().setAnalysisLogsEnabled(false);
    logOutput.log("test", ClientLogOutput.Level.INFO);
    verifyNoInteractions(mockConsole);
  }

  @Test
  void testInfo() {
    logOutput.log("test", ClientLogOutput.Level.INFO);
    verify(mockConsole).info("test");
  }

  @Test
  void testError() {
    logOutput.log("test", ClientLogOutput.Level.ERROR);
    verify(mockConsole).error("test");
  }

  @Test
  void testWarn() {
    logOutput.log("test", ClientLogOutput.Level.WARN);
    verify(mockConsole).info("test");
  }

  @Test
  void testTrace() {
    logOutput.log("test", ClientLogOutput.Level.TRACE);
    verify(mockConsole).debug("test");
  }
}
