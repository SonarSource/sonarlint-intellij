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
import javax.annotation.Nullable;
import org.sonarlint.intellij.analysis.AnalysisErrorCallback;
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
    submitFiles(Arrays.asList(openFiles), trigger, true);
  }

  /**
   * Submit files for analysis.
   * This is a user-initiated action. It will start running in foreground, modal (blocking) and will consume the single slot, updating
   * the status of the associated actions / icons.
   * @param files Files to be analyzed
   * @param trigger What triggered the analysis
   */
  public void submitFilesModal(Collection<VirtualFile> files, TriggerType trigger) {
    submitFilesModal(files, trigger, null);
  }

  public void submitFilesModal(Collection<VirtualFile> files, TriggerType trigger, @Nullable AnalysisErrorCallback callback) {
    Multimap<Module, VirtualFile> filesByModule = filterAndgetByModule(files, false);

    if (!filesByModule.isEmpty()) {
      console.debug("Trigger: " + trigger);

      for (Module m : filesByModule.keySet()) {
        sonarLintJobManager.submitManual(m, filesByModule.get(m), trigger, true, callback);
      }
    }
  }

  /**
   * Submit files for analysis.
   * @param files Files to be analyzed.
   * @param trigger What triggered the analysis
   * @param startInBackground Whether the analysis was triggered automatically. It affects the filter of files that should be analysed and also
   *                    if it starts in background or foreground.
   */
  public void submitFiles(Collection<VirtualFile> files, TriggerType trigger, boolean startInBackground) {
    submitFiles(files, trigger, null, startInBackground);
  }

  public void submitFiles(Collection<VirtualFile> files, TriggerType trigger, @Nullable AnalysisErrorCallback callback, boolean startInBackground) {
    Multimap<Module, VirtualFile> filesByModule = filterAndgetByModule(files, startInBackground);

    if (!filesByModule.isEmpty()) {
      console.debug("Trigger: " + trigger);

      for (Module m : filesByModule.keySet()) {
        if (startInBackground) {
          sonarLintJobManager.submitBackground(m, filesByModule.get(m), trigger, callback);
        } else {
          sonarLintJobManager.submitManual(m, filesByModule.get(m), trigger, false, callback);
        }
      }
    }
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
