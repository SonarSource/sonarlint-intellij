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

import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

import javax.annotation.Nullable;
import java.util.UUID;

public class MakeTrigger implements BuildManagerListener, CompilationStatusListener, StartupActivity {
  private Project project;

  @Override
  public void runActivity(@NotNull Project project) {
    this.project = project;
    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect(project);
    busConnection.subscribe(BuildManagerListener.TOPIC, this);
    busConnection.subscribe(CompilerTopics.COMPILATION_STATUS, this);
  }

  @Override
  public void beforeBuildProcessStarted(Project project, UUID sessionId) {
    //nothing to do
  }

  @Override public void buildStarted(Project project, UUID sessionId, boolean isAutomake) {
    // nothing to do
  }

  @Override public void buildFinished(@Nullable Project project, UUID sessionId, boolean isAutomake) {
    // project is null in DummyCompileContext
    if (project == null || !project.equals(this.project) || !isAutomake) {
      // covered by compilationFinished
      return;
    }

    SonarLintUtils.getService(project, SonarLintConsole.class).debug("build finished");
    SonarLintUtils.getService(project, SonarLintSubmitter.class).submitOpenFilesAuto(TriggerType.COMPILATION);
  }

  /**
   * Does not get called for Automake.
   * {@link CompileContext} can have a null Project. See {@link com.intellij.openapi.compiler.DummyCompileContext}.
   */
  @Override public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
    if (compileContext.getProject().equals(this.project)) {
      SonarLintUtils.getService(project, SonarLintConsole.class).debug("compilation finished");
      SonarLintUtils.getService(project, SonarLintSubmitter.class).submitOpenFilesAuto(TriggerType.COMPILATION);
    }
  }

  @Override public void fileGenerated(String outputRoot, String relativePath) {
    // nothing to do
  }
}
