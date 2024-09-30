/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.VirtualFileUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ProgressUtils.waitForFuture;

@Service(Service.Level.PROJECT)
public final class SonarLintAnalyzer {
  private final Project myProject;

  public SonarLintAnalyzer(Project project) {
    myProject = project;
  }

  public ModuleAnalysisResult analyzeModule(Module module, Collection<VirtualFile> filesToAnalyze, AnalysisState analysisState, ProgressIndicator indicator,
    boolean shouldFetchServerIssues) {
    // Configure plugin properties. Nothing might be done if there is no configurator available for the extensions loaded in runtime.
    var start = System.currentTimeMillis();
    var console = getService(myProject, SonarLintConsole.class);

    var contributedConfigurations = getConfigurationFromConfiguratorEP(module, filesToAnalyze, console);
    var contributedProperties = collectContributedExtraProperties(module, console, contributedConfigurations);

    // configure files
    var inputFiles = getInputFiles(module, filesToAnalyze);
    if (inputFiles == null || inputFiles.isEmpty()) {
      return new ModuleAnalysisResult(Collections.emptyList());
    }

    // Analyze
    try {
      getService(myProject, RunningAnalysesTracker.class).cancelSimilarAnalysis(analysisState, console);
      getService(myProject, RunningAnalysesTracker.class).track(analysisState);

      var what = filesToAnalyze.size() == 1 ? String.format("'%s'", filesToAnalyze.iterator().next().getName()) : String.format("%d files", filesToAnalyze.size());
      console.info("Analysing " + what + " (ID " + analysisState.getId() + ")...");

      var analysisTask = getService(BackendService.class).analyzeFilesAndTrack(module, analysisState.getId(), inputFiles, contributedProperties, shouldFetchServerIssues, start);

      AnalyzeFilesResponse result = null;
      try {
        result = waitForFuture(indicator, analysisTask);
      } catch (ProcessCanceledException e) {
        getService(myProject, RunningAnalysesTracker.class).finish(analysisState);
        console.debug("Analysis " + analysisState.getId() + " canceled");
      } catch (Exception e) {
        getService(myProject, RunningAnalysesTracker.class).finish(analysisState);
        console.error("Error during analysis ID " + analysisState.getId(), e);
      }

      Set<VirtualFile> failedAnalysisFiles = Collections.emptySet();
      if (result != null) {
        failedAnalysisFiles = result.getFailedAnalysisFiles().stream()
          .map(VirtualFileUtils.INSTANCE::uriToVirtualFile).filter(Objects::nonNull).collect(Collectors.toSet());
      }

      return new ModuleAnalysisResult(failedAnalysisFiles);
    } finally {
      console.debug("Analysis " + analysisState.getId() + " finished");
    }
  }

  @NotNull
  private static Map<String, String> collectContributedExtraProperties(Module module, SonarLintConsole console,
    List<AnalysisConfigurator.AnalysisConfiguration> contributedConfigurations) {
    var contributedProperties = new HashMap<String, String>();
    for (var config : contributedConfigurations) {
      for (var entry : config.extraProperties.entrySet()) {
        if (contributedProperties.containsKey(entry.getKey()) && !Objects.equals(contributedProperties.get(entry.getKey()), entry.getValue())) {
          console.error("The same property " + entry.getKey() + " was contributed by multiple configurators with different values: " +
            contributedProperties.get(entry.getKey()) + " / " + entry.getValue());
        }
        contributedProperties.put(entry.getKey(), entry.getValue());
      }
    }
    var extraProperties = Settings.getSettingsFor(module.getProject()).getAdditionalProperties();
    for (var entry : extraProperties.entrySet()) {
      if (contributedProperties.containsKey(entry.getKey()) && !Objects.equals(contributedProperties.get(entry.getKey()), entry.getValue())) {
        console.error("The same property " + entry.getKey() + " was contributed by multiple configurators with different values: " +
          contributedProperties.get(entry.getKey()) + " / " + entry.getValue());
      }
      contributedProperties.put(entry.getKey(), entry.getValue());
    }
    return contributedProperties;
  }

  @NotNull
  private static List<AnalysisConfigurator.AnalysisConfiguration> getConfigurationFromConfiguratorEP(Module module, Collection<VirtualFile> filesToAnalyze,
    SonarLintConsole console) {
    return AnalysisConfigurator.EP_NAME.getExtensionList().stream()
      .map(config -> {
        console.debug("Configuring analysis with " + config.getClass().getName());
        return config.configure(module, filesToAnalyze);
      })
      .toList();
  }

  private static List<URI> getInputFiles(Module module, Collection<VirtualFile> filesToAnalyze) {
    return computeReadActionSafely(module.getProject(), () -> filesToAnalyze.stream()
      .map(f -> createClientInputFile(module, f))
      .filter(Objects::nonNull)
      .toList());
  }

  @CheckForNull
  private static URI createClientInputFile(Module module, VirtualFile virtualFile) {
    var relativePath = SonarLintAppUtils.getRelativePathForAnalysis(module, virtualFile);
    if (relativePath != null) {
      return createURI(virtualFile);
    }
    return null;
  }

  private static URI createURI(VirtualFile file) {
    var uri = VirtualFileUtils.INSTANCE.toURI(file);
    if (uri == null) {
      throw new IllegalStateException("Not a local file");
    }
    return uri;
  }

}
