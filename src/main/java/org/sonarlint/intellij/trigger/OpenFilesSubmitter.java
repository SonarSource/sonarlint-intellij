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
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;

public class OpenFilesSubmitter extends AbstractProjectComponent {
  private final SonarLintConsole console;
  private final FileEditorManager editorManager;
  private final SonarLintJobManager sonarLintJobManager;
  private final SonarLintGlobalSettings globalSettings;
  private final SonarLintAppUtils utils;

  public OpenFilesSubmitter(Project project, SonarLintConsole console, FileEditorManager editorManager,
    SonarLintJobManager sonarLintJobManager, SonarLintGlobalSettings globalSettings, SonarLintAppUtils utils) {
    super(project);
    this.console = console;
    this.editorManager = editorManager;
    this.sonarLintJobManager = sonarLintJobManager;
    this.globalSettings = globalSettings;
    this.utils = utils;
  }

  public void submit(TriggerType trigger) {
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    submitFiles(openFiles, trigger);
  }

  public void submitIfAutoEnabled(TriggerType trigger) {
    if (!globalSettings.isAutoTrigger()) {
      return;
    }
    submit(trigger);
  }

  private void submitFiles(VirtualFile[] files, TriggerType trigger) {
    Multimap<Module, VirtualFile> filesByModule = HashMultimap.create();

    for (VirtualFile file : files) {
      Module m = utils.findModuleForFile(file, myProject);
      if (!utils.shouldAnalyzeAutomatically(file, m)) {
        continue;
      }

      filesByModule.put(m, file);
    }

    if (!filesByModule.isEmpty()) {
      console.debug("Trigger: " + trigger);

      for (Module m : filesByModule.keySet()) {
        sonarLintJobManager.submitAsync(m, filesByModule.get(m), trigger);
      }
    }
  }
}
