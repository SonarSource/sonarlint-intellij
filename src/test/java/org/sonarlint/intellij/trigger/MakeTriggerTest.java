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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.ui.SonarLintConsole;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class MakeTriggerTest extends SonarTest {
  @Mock
  SonarLintSubmitter submitter;
  @Mock
  private Project project;
  @Mock
  private SonarLintConsole console;
  @Mock
  private CompileContext context;
  @Mock
  private CompilerManager compilerManager;

  private MakeTrigger trigger;


  @Before
  public void setUp() {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    SonarLintTestUtils.mockMessageBus(app);
    trigger = new MakeTrigger(project, submitter, console, compilerManager);
    when(context.getProject()).thenReturn(project);
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

  @Test
  public void should_register_compiler_manager() {
    trigger.projectOpened();
    verify(compilerManager).addCompilationStatusListener(any(CompilationStatusListener.class));

    trigger.projectClosed();
    verify(compilerManager).removeCompilationStatusListener(any(CompilationStatusListener.class));
  }
}
