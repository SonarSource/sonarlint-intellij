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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.analysis.VirtualFileTestPredicate;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintSubmitter extends AbstractProjectComponent {
  private final SonarLintConsole console;
  private final FileEditorManager editorManager;
  private final SonarLintJobManager sonarLintJobManager;
  private final SonarLintGlobalSettings globalSettings;
  private final SonarLintAppUtils utils;
  private final LocalFileExclusions localFileExclusions;
  private final ProjectBindingManager projectBindingManager;

  public SonarLintSubmitter(Project project, SonarLintConsole console, FileEditorManager editorManager,
    SonarLintJobManager sonarLintJobManager, SonarLintGlobalSettings globalSettings, SonarLintAppUtils utils,
    LocalFileExclusions localFileExclusions, ProjectBindingManager projectBindingManager) {
    super(project);
    this.console = console;
    this.editorManager = editorManager;
    this.sonarLintJobManager = sonarLintJobManager;
    this.globalSettings = globalSettings;
    this.utils = utils;
    this.localFileExclusions = localFileExclusions;
    this.projectBindingManager = projectBindingManager;
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
   *
   * @param files   Files to be analyzed
   * @param trigger What triggered the analysis
   */
  public void submitFilesModal(Collection<VirtualFile> files, TriggerType trigger) {
    submitFilesModal(files, trigger, null);
  }

  public void submitFilesModal(Collection<VirtualFile> files, TriggerType trigger, @Nullable AnalysisCallback callback) {
    try {
      Map<Module, Collection<VirtualFile>> filesByModule = filterAndGetByModule(files, false);

      if (!filesByModule.isEmpty()) {
        console.debug("Trigger: " + trigger);
        sonarLintJobManager.submitManual(filesByModule, trigger, true, callback);
      }
    } catch (InvalidBindingException e) {
      // nothing to do, SonarLintEngineManager already showed notification
    }
  }

  /**
   * Submit files for analysis.
   *
   * @param files             Files to be analyzed.
   * @param trigger           What triggered the analysis
   * @param startInBackground Whether the analysis was triggered automatically. It affects the filter of files that should be analyzed and also
   *                          if it starts in background or foreground.
   */
  public void submitFiles(Collection<VirtualFile> files, TriggerType trigger, boolean startInBackground) {
    submitFiles(files, trigger, null, startInBackground);
  }

  public void submitFiles(Collection<VirtualFile> files, TriggerType trigger, @Nullable AnalysisCallback callback, boolean startInBackground) {
    boolean checkExclusions = trigger != TriggerType.ACTION;
    try {
      Map<Module, Collection<VirtualFile>> filesByModule = filterAndGetByModule(files, checkExclusions);

      if (!filesByModule.isEmpty()) {
        console.debug("Trigger: " + trigger);
        if (startInBackground) {
          sonarLintJobManager.submitBackground(filesByModule, trigger, callback);
        } else {
          sonarLintJobManager.submitManual(filesByModule, trigger, false, callback);
        }
      }
    } catch (InvalidBindingException e) {
      // nothing to do, SonarLintEngineManager already showed notification
    }
  }

  private Map<Module, Collection<VirtualFile>> filterAndGetByModule(Collection<VirtualFile> files, boolean checkExclusions) throws InvalidBindingException {
    Map<Module, Collection<VirtualFile>> filesByModule = new HashMap<>();
    SonarLintFacade sonarLintFacade = projectBindingManager.getFacade();

    for (VirtualFile file : files) {
      Computable<Module> c = () -> utils.findModuleForFile(file, myProject);
      Module m = ApplicationManager.getApplication().runReadAction(c);
      if (checkExclusions) {
        LocalFileExclusions.Result result = localFileExclusions.checkExclusions(file, m);
        if (result.isExcluded()) {
          if (result.excludeReason() != null) {
            logExclusion(file, "excluded: " + result.excludeReason());
          }
          continue;
        }
      } else {
        LocalFileExclusions.Result result = localFileExclusions.canAnalyze(file, m);
        if (result.isExcluded()) {
          logExclusion(file, "excluded: " + result.excludeReason());
          continue;
        }
      }

      filesByModule.computeIfAbsent(m, mod -> new LinkedHashSet<>()).add(file);
    }

    // Apply server file exclusions. This is an expensive operation, so we call the core only once per module.
    if (checkExclusions) {
      // Note: iterating over a copy of keys, because removal of last value removes the key,
      // resulting in ConcurrentModificationException
      List<Module> modules = new ArrayList<>(filesByModule.keySet());
      for (Module module : modules) {
        Collection<VirtualFile> virtualFiles = filesByModule.get(module);
        VirtualFileTestPredicate testPredicate = SonarLintUtils.get(module, VirtualFileTestPredicate.class);
        Collection<VirtualFile> excluded = sonarLintFacade.getExcluded(module, virtualFiles, testPredicate);
        for (VirtualFile f : excluded) {
          logExclusion(f, "not automatically analyzed due to exclusions configured in the SonarQube Server");
        }
        virtualFiles.removeAll(excluded);
        if (virtualFiles.isEmpty()) {
          filesByModule.remove(module);
        }
      }
    }

    return filesByModule;
  }

  private void logExclusion(VirtualFile f, String reason) {
    console.debug("File '" + f.getName() + "' " + reason);

  }
}
