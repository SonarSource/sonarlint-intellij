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
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;

public class SonarLintSubmitter extends AbstractProjectComponent {
  private final SonarLintConsole console;
  private final FileEditorManager editorManager;
  private final SonarLintJobManager sonarLintJobManager;
  private final SonarLintGlobalSettings globalSettings;
  private final SonarLintAppUtils utils;

  public SonarLintSubmitter(Project project, SonarLintConsole console, FileEditorManager editorManager,
    SonarLintJobManager sonarLintJobManager, SonarLintGlobalSettings globalSettings, SonarLintAppUtils utils) {
    super(project);
    this.console = console;
    this.editorManager = editorManager;
    this.sonarLintJobManager = sonarLintJobManager;
    this.globalSettings = globalSettings;
    this.utils = utils;
  }

  public void submitOpenFilesAuto(TriggerType trigger) {
    if (!globalSettings.isAutoTrigger()) {
      return;
    }
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    submitFiles(Arrays.asList(openFiles), trigger, true, false);
  }

  /**
   * Submit files for analysis.
   * @param files Files to be analyzed. If empty, nothing is done and an empty AnalysisResult is returned.
   * @param trigger What triggered the analysis
   * @param autoTrigger Whether the analysis was triggered automatically. It affects the filter of files that should be analysed.
   * @param manual Whether this is a user-initiated action. If it is, it will start running in foreground and will consume the single slot, updating
   *               the status of the associated actions / icons.
   */
  public CompletableFuture<AnalysisResult> submitFiles(Collection<VirtualFile> files, TriggerType trigger, boolean autoTrigger, boolean manual) {
    Multimap<Module, VirtualFile> filesByModule = filterAndgetByModule(files, autoTrigger);

    if (!filesByModule.isEmpty()) {
      console.debug("Trigger: " + trigger);

      for (Module m : filesByModule.keySet()) {
        if (manual) {
          return sonarLintJobManager.submitManual(m, filesByModule.get(m), trigger);
        } else {
          return sonarLintJobManager.submitBackground(m, filesByModule.get(m), trigger);
        }
      }
    }

    CompletableFuture<AnalysisResult> future = new CompletableFuture<>();
    future.complete(new AnalysisResult(0, Collections.emptyMap()));
    return future;
  }

  private Multimap<Module, VirtualFile> filterAndgetByModule(Collection<VirtualFile> files, boolean autoTrigger) {
    Multimap<Module, VirtualFile> filesByModule = HashMultimap.create();

    for (VirtualFile file : files) {
      Module m = utils.findModuleForFile(file, myProject);
      if (autoTrigger) {
        if (!utils.shouldAnalyzeAutomatically(file, m)) {
          continue;
        }
      } else {
        if (!utils.shouldAnalyze(file, m)) {
          console.info("File can't be analyzed. Skipping: " + file.getPath());
          continue;
        }
      }

      filesByModule.put(m, file);
    }

    return filesByModule;
  }
}
