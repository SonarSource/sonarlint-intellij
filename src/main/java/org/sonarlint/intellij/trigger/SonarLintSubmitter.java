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
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;

public class SonarLintSubmitter extends AbstractProjectComponent {
  private final SonarLintConsole console;
  private final FileEditorManager editorManager;
  private final SonarLintTelemetry telemetry;
  private final SonarLintJobManager sonarLintJobManager;
  private final SonarLintGlobalSettings globalSettings;
  private final SonarLintAppUtils utils;

  public SonarLintSubmitter(Project project, SonarLintConsole console, FileEditorManager editorManager, SonarLintTelemetry telemetry,
    SonarLintJobManager sonarLintJobManager, SonarLintGlobalSettings globalSettings, SonarLintAppUtils utils) {
    super(project);
    this.console = console;
    this.editorManager = editorManager;
    this.telemetry = telemetry;
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

  public void submitFilesModal(Collection<VirtualFile> files, TriggerType trigger, @Nullable AnalysisCallback callback) {
    telemetry.analysisSubmitted();
    Map<Module, Collection<VirtualFile>> filesByModule = filterAndgetByModule(files, false);

    if (!filesByModule.isEmpty()) {
      console.debug("Trigger: " + trigger);
      sonarLintJobManager.submitManual(filesByModule, trigger, true, callback);
    }
  }

  /**
   * Submit files for analysis.
   * @param files Files to be analyzed.
   * @param trigger What triggered the analysis
   * @param startInBackground Whether the analysis was triggered automatically. It affects the filter of files that should be analyzed and also
   *                    if it starts in background or foreground.
   */
  public void submitFiles(Collection<VirtualFile> files, TriggerType trigger, boolean startInBackground) {
    submitFiles(files, trigger, null, startInBackground);
  }

  public void submitFiles(Collection<VirtualFile> files, TriggerType trigger, @Nullable AnalysisCallback callback, boolean startInBackground) {
    telemetry.analysisSubmitted();
    Map<Module, Collection<VirtualFile>> filesByModule = filterAndgetByModule(files, startInBackground);

    if (!filesByModule.isEmpty()) {
      console.debug("Trigger: " + trigger);
      if (startInBackground) {
        sonarLintJobManager.submitBackground(filesByModule, trigger, callback);
      } else {
        sonarLintJobManager.submitManual(filesByModule, trigger, false, callback);
      }
    }
  }

  private Map<Module, Collection<VirtualFile>> filterAndgetByModule(Collection<VirtualFile> files, boolean autoTrigger) {
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

    return filesByModule.asMap();
  }
}
