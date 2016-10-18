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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

public class MakeTrigger extends AbstractProjectComponent implements BuildManagerListener, CompilationStatusListener {
  private final FileEditorManager editorManager;
  private final SonarLintJobManager analyzer;
  private final SonarLintConsole console;

  public MakeTrigger(Project project, FileEditorManager editorManager, SonarLintJobManager analyzer, SonarLintConsole console) {
    super(project);
    this.editorManager = editorManager;
    this.analyzer = analyzer;
    this.console = console;
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(BuildManagerListener.TOPIC, this);
  }

  // introduced with IDEA 15
  public void beforeBuildProcessStarted(Project project, UUID sessionId) {
    //nothing to do
  }

  @Override public void buildStarted(Project project, UUID sessionId, boolean isAutomake) {
    // nothing to do
  }

  @Override public void buildFinished(Project project, UUID sessionId, boolean isAutomake) {
    if (!isAutomake) {
      // covered by compilationFinished
      return;
    }

    SonarLintGlobalSettings globalSettings = SonarLintUtils.get(SonarLintGlobalSettings.class);
    if (!globalSettings.isAutoTrigger()) {
      return;
    }

    VirtualFile[] openFiles = editorManager.getOpenFiles();
    submitFiles(openFiles, "project build");
  }

  private void submitFiles(VirtualFile[] files, String trigger) {
    Multimap<Module, VirtualFile> filesByModule = HashMultimap.create();

    for (VirtualFile file : files) {
      Module m = ModuleUtil.findModuleForFile(file, myProject);
      if (!SonarLintUtils.shouldAnalyzeAutomatically(file, m)) {
        continue;
      }

      filesByModule.put(m, file);
    }

    if (!filesByModule.isEmpty()) {
      console.debug("Trigger: " + trigger);

      for (Module m : filesByModule.keySet()) {
        analyzer.submitAsync(m, filesByModule.get(m), TriggerType.COMPILATION);
      }
    }
  }

  /**
   * Does not get called for Automake
   */
  @Override public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
    SonarLintGlobalSettings globalSettings = SonarLintUtils.get(SonarLintGlobalSettings.class);
    if (!globalSettings.isAutoTrigger()) {
      return;
    }

    VirtualFile[] openFiles = editorManager.getOpenFiles();
    submitFiles(openFiles, "compilation");
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
    CompilerManager.getInstance(super.myProject).addCompilationStatusListener(this);
  }

  @Override
  public void projectClosed() {
    CompilerManager.getInstance(super.myProject).removeCompilationStatusListener(this);
  }

}
