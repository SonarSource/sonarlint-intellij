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

import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.ui.SonarLintConsole;

public class MakeTrigger extends AbstractProjectComponent implements BuildManagerListener, CompilationStatusListener {
  private final SonarLintConsole console;
  private final CompilerManager compilerManager;
  private final SonarLintSubmitter submitter;

  public MakeTrigger(Project project, SonarLintSubmitter submitter, SonarLintConsole console, CompilerManager compilerManager) {
    super(project);
    this.submitter = submitter;
    this.console = console;
    this.compilerManager = compilerManager;
    ApplicationManager.getApplication().getMessageBus().connect(project).subscribe(BuildManagerListener.TOPIC, this);
  }

  // introduced with IDEA 15
  public void beforeBuildProcessStarted(Project project, UUID sessionId) {
    //nothing to do
  }

  @Override public void buildStarted(Project project, UUID sessionId, boolean isAutomake) {
    // nothing to do
  }

  @Override public void buildFinished(Project project, UUID sessionId, boolean isAutomake) {
    if (!project.equals(myProject) || !isAutomake) {
      // covered by compilationFinished
      return;
    }

    console.debug("build finished");
    submitter.submitOpenFilesAuto(TriggerType.COMPILATION);
  }

  /**
   * Does not get called for Automake.
   * {@link CompileContext} can have a null Project. See {@link com.intellij.openapi.compiler.DummyCompileContext}.
   */
  @Override public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
    Project project = compileContext.getProject();
    if (project != null && project.equals(myProject)) {
      console.debug("compilation finished");
      submitter.submitOpenFilesAuto(TriggerType.COMPILATION);
    }
  }

  @Override public void fileGenerated(String outputRoot, String relativePath) {
    // nothing to do
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "MakeTrigger";
  }

  @Override
  public void projectOpened() {
    compilerManager.addCompilationStatusListener(this);
  }

  @Override
  public void projectClosed() {
    compilerManager.removeCompilationStatusListener(this);
  }

}
