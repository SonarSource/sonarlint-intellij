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
package org.sonarlint.intellij.ui;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SonarLintConsoleImplementationTest extends AbstractSonarLintLightTests {
  private ConsoleView consoleView = mock(ConsoleView.class);
  private SonarLintConsole console;

  @Before
  public void prepare() {
    console = new SonarLintConsoleImpl(getProject(), consoleView);
  }

  @Test
  public void should_clear_the_console_view() {
    console.clear();

    verify(consoleView).clear();
  }

  @Test
  public void debug_logging_should_be_enabled_if_verbose_enabled_in_project_settings() {
    getProjectSettings().setVerboseEnabled(true);

    assertThat(console.debugEnabled()).isTrue();
    console.debug("debug msg");
    verify(consoleView).print("debug msg\n", ConsoleViewContentType.NORMAL_OUTPUT);
  }

  @Test
  public void debug_logging_should_be_disabled_if_verbose_disabled_in_project_settings() {
    getProjectSettings().setVerboseEnabled(false);

    assertThat(console.debugEnabled()).isFalse();
    console.debug("debug msg");
    verifyZeroInteractions(consoleView);
  }

  @Test
  public void logging_should_print_to_console() {
    getProjectSettings().setVerboseEnabled(true);
    console.info("info msg");
    verify(consoleView).print("info msg\n", ConsoleViewContentType.NORMAL_OUTPUT);

    console.error("error msg");
    verify(consoleView).print("error msg\n", ConsoleViewContentType.ERROR_OUTPUT);

    console.error("error with exception", new IllegalStateException("ex"));
    verify(consoleView).print("error with exception\n", ConsoleViewContentType.ERROR_OUTPUT);
  }

  @Test
  public void testGetter() {
    assertThat(console.getConsoleView()).isEqualTo(consoleView);
  }
}
