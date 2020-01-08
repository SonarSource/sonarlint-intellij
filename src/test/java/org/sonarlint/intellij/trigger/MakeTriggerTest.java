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

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.project.Project;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.ui.SonarLintConsole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class MakeTriggerTest extends SonarTest {
  private SonarLintSubmitter submitter = mock(SonarLintSubmitter.class);
  private Project project = mock(Project.class);
  private SonarLintConsole console = mock(SonarLintConsole.class);
  private CompileContext context = mock(CompileContext.class);

  private MakeTrigger trigger;

  @Before
  public void prepare() {
    when(context.getProject()).thenReturn(project);
    trigger = new MakeTrigger(project, submitter, console);
  }

  @Test
  public void should_trigger_on_compilation() {
    trigger.compilationFinished(false, 0, 0, context);
    verify(submitter).submitOpenFilesAuto(TriggerType.COMPILATION);
  }

  @Test
  public void should_trigger_automake() {
    when(context.getProject()).thenReturn(mock(Project.class));
    trigger.buildFinished(project, UUID.randomUUID(), true);
    verify(submitter).submitOpenFilesAuto(TriggerType.COMPILATION);
  }

  @Test
  public void should_do_nothing_on_generate() {
    trigger.fileGenerated("output", "relative");
    verifyZeroInteractions(submitter);
  }

  @Test
  public void handle_null_project() {
    // this doesn't comply with the interface but it's null in DummyCompileContext
    when(context.getProject()).thenReturn(null);
    trigger.compilationFinished(false, 0, 0, context);
    trigger.buildFinished(null, UUID.randomUUID(), true);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void component_name() {
    assertThat(trigger.getComponentName()).isEqualTo("MakeTrigger");
  }

  @Test
  public void should_not_trigger_if_another_project() {
    when(context.getProject()).thenReturn(mock(Project.class));
    trigger.compilationFinished(false, 0, 0, context);
    trigger.buildFinished(mock(Project.class), UUID.randomUUID(), true);

    verifyZeroInteractions(submitter);
  }

  @Test
  public void should_not_trigger_if_not_automake() {
    when(context.getProject()).thenReturn(mock(Project.class));
    trigger.buildFinished(project, UUID.randomUUID(), false);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void other_events_should_be_noop() {
    trigger.buildStarted(project, UUID.randomUUID(), true);
    verifyZeroInteractions(submitter);
  }
}
