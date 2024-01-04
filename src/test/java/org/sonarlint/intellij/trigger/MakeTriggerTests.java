/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.project.Project;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MakeTriggerTests extends AbstractSonarLintLightTests {
  private AnalysisSubmitter submitter = mock(AnalysisSubmitter.class);
  private SonarLintConsole console = mock(SonarLintConsole.class);
  private CompileContext context = mock(CompileContext.class);

  private MakeTrigger trigger;

  @BeforeEach
  void prepare() {
    replaceProjectService(AnalysisSubmitter.class, submitter);
    replaceProjectService(SonarLintConsole.class, console);
    when(context.getProject()).thenReturn(getProject());
    trigger = new MakeTrigger();
    trigger.runActivity(getProject());
  }

  @Test
  void should_trigger_on_compilation() {
    trigger.compilationFinished(false, 0, 0, context);
    verify(submitter).autoAnalyzeOpenFiles(TriggerType.COMPILATION);
  }

  @Test
  void should_trigger_automake() {
    trigger.buildFinished(getProject(), UUID.randomUUID(), true);
    verify(submitter).autoAnalyzeOpenFiles(TriggerType.COMPILATION);
  }

  @Test
  void should_do_nothing_on_generate() {
    trigger.fileGenerated("output", "relative");
    verifyNoInteractions(submitter);
  }

  @Test
  void should_not_trigger_if_another_project() {
    when(context.getProject()).thenReturn(mock(Project.class));
    trigger.compilationFinished(false, 0, 0, context);
    trigger.buildFinished(mock(Project.class), UUID.randomUUID(), true);

    verifyNoInteractions(submitter);
  }

  @Test
  void should_not_trigger_if_not_automake() {
    when(context.getProject()).thenReturn(mock(Project.class));
    trigger.buildFinished(getProject(), UUID.randomUUID(), false);
    verifyNoInteractions(submitter);
  }

  @Test
  void other_events_should_be_noop() {
    trigger.buildStarted(getProject(), UUID.randomUUID(), true);
    verifyNoInteractions(submitter);
  }
}
