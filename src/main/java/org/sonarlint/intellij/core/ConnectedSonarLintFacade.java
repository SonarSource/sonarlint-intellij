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
package org.sonarlint.intellij.core;

import com.google.common.base.Preconditions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;

class ConnectedSonarLintFacade extends SonarLintFacade {
  private final ConnectedSonarLintEngine sonarlint;

  ConnectedSonarLintFacade(ConnectedSonarLintEngine engine, Project project) {
    super(project);
    Preconditions.checkNotNull(project, "project");
    Preconditions.checkNotNull(project.getBasePath(), "project base path");
    Preconditions.checkNotNull(engine, "engine");
    this.sonarlint = engine;
  }

  @Override
  protected AnalysisResults analyze(Path baseDir, Path workDir, Collection<ClientInputFile> inputFiles, Map<String, String> props,
    IssueListener issueListener, ProgressMonitor progressMonitor) {
    ConnectedAnalysisConfiguration config = ConnectedAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFiles(inputFiles)
      .setProjectKey(SonarLintUtils.getService(project, SonarLintProjectSettings.class).getProjectKey())
      .putAllExtraProperties(props)
      .build();
    SonarLintConsole console = SonarLintUtils.getService(project, SonarLintConsole.class);
    console.debug("Starting analysis with configuration:\n" + config.toString());

    return sonarlint.analyze(config, issueListener, null, progressMonitor);
  }

  @Override
  public Collection<VirtualFile> getExcluded(Module module, Collection<VirtualFile> files, Predicate<VirtualFile> testPredicate) {
    ModuleBindingManager bindingManager = SonarLintUtils.getService(module, ModuleBindingManager.class);
    ProjectBinding binding = bindingManager.getBinding();
    if (binding == null) {
      // should never happen since the project should be bound!
      return Collections.emptyList();
    }

    Function<VirtualFile, String> ideFilePathExtractor = s -> SonarLintAppUtils.getPathRelativeToProjectBaseDir(module.getProject(), s);
    return sonarlint.getExcludedFiles(binding, files, ideFilePathExtractor, testPredicate);
  }

  @Override
  public List<PluginDetails> getLoadedAnalyzers() {
    return sonarlint.getPluginDetails().stream().filter(p -> !p.skipReason().isPresent()).collect(Collectors.toList());
  }

  @Override
  public RuleDetails ruleDetails(String ruleKey) {
    return sonarlint.getRuleDetails(ruleKey);
  }

}
