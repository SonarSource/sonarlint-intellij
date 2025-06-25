/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.common.ui.SonarLintConsole;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class CompilationFinishedAnalysisTrigger implements CompilationStatusListener {

  /**
   * Does not get called for Automake.
   * {@link CompileContext} can have a null Project. See {@link com.intellij.openapi.compiler.DummyCompileContext}.
   */
  @Override
  public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
    var compiledProject = compileContext.getProject();
    getService(compiledProject, SonarLintConsole.class).debug("compilation finished");
    runOnPooledThread(compiledProject, () -> getService(compiledProject, AnalysisSubmitter.class).autoAnalyzeSelectedFiles());
  }
}
