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
package org.sonarlint.intellij.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalysisLogOutputTests extends AbstractSonarLintLightTests {
  private final SonarLintConsole mockConsole = mock(SonarLintConsole.class);
  private AnalysisLogOutput logOutput;

  @BeforeEach
  void prepare() {
    replaceProjectService(SonarLintConsole.class, mockConsole);
    getProjectSettings().setVerboseEnabled(true);
    logOutput = new AnalysisLogOutput(getProject());
    reset(mockConsole);
  }

  @ParameterizedTest
  @CsvSource({
    "DEBUG, debug",
    "INFO,  info",
    "ERROR, error",
    "WARN,  info",
    "TRACE, trace"
  })
  void test_log_levels(ClientLogOutput.Level level, String expectedMethod) {
    logOutput.log("test", level);
    
    switch (expectedMethod) {
      case "trace" -> verify(mockConsole, never()).debug("test");
      case "debug" -> verify(mockConsole).debug("test");
      case "info" -> verify(mockConsole).info("test");
      case "error" -> verify(mockConsole).error("test");
      default -> throw new IllegalArgumentException("Unexpected value: " + expectedMethod);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "test message", "null"})
  void test_log_with_different_messages(String message) {
    var actualMessage = "null".equals(message) ? null : message;
    logOutput.log(actualMessage, ClientLogOutput.Level.INFO);
    verify(mockConsole).info(actualMessage);
  }

  @Test
  void test_no_log_when_project_is_null() {
    logOutput = new AnalysisLogOutput(null);
    
    logOutput.log("test", ClientLogOutput.Level.INFO);
    verifyNoInteractions(mockConsole);
  }

  @Test
  void test_all_log_levels_when_enabled() {
    getProjectSettings().setVerboseEnabled(true);

    logOutput.log("debug msg", ClientLogOutput.Level.DEBUG);
    logOutput.log("info msg", ClientLogOutput.Level.INFO);
    logOutput.log("warn msg", ClientLogOutput.Level.WARN);
    logOutput.log("error msg", ClientLogOutput.Level.ERROR);
    logOutput.log("trace msg", ClientLogOutput.Level.TRACE);
    
    verify(mockConsole).debug("debug msg");
    verify(mockConsole).info("info msg");
    verify(mockConsole).info("warn msg");
    verify(mockConsole).error("error msg");
    verify(mockConsole, never()).debug("trace msg");
  }

  @Test
  void test_all_log_levels_when_disabled() {
    getProjectSettings().setVerboseEnabled(false);
    
    logOutput.log("debug msg", ClientLogOutput.Level.DEBUG);
    logOutput.log("info msg", ClientLogOutput.Level.INFO);
    logOutput.log("warn msg", ClientLogOutput.Level.WARN);
    logOutput.log("error msg", ClientLogOutput.Level.ERROR);
    logOutput.log("trace msg", ClientLogOutput.Level.TRACE);
    
    verifyNoInteractions(mockConsole);
  }

  @Test
  void test_close() {
    logOutput.close();
    
    logOutput.log("test", ClientLogOutput.Level.INFO);
    verifyNoInteractions(mockConsole);
  }

  @Test
  void test_multiple_log_calls() {
    logOutput.log("first", ClientLogOutput.Level.INFO);
    logOutput.log("second", ClientLogOutput.Level.ERROR);
    logOutput.log("third", ClientLogOutput.Level.DEBUG);
    
    verify(mockConsole).info("first");
    verify(mockConsole).error("second");
    verify(mockConsole).debug("third");
  }
}
