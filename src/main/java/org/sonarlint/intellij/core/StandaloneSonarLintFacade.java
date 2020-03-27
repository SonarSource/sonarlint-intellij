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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.ProjectLogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

final class StandaloneSonarLintFacade extends SonarLintFacade {
  private final StandaloneSonarLintEngine sonarlint;
  private final SonarLintConsole console;
  private final SonarLintGlobalSettings globalSettings;

  StandaloneSonarLintFacade(SonarLintGlobalSettings globalSettings, SonarLintProjectSettings projectSettings, SonarLintConsole console, Project project,
    StandaloneSonarLintEngine engine) {
    super(project, projectSettings);
    Preconditions.checkNotNull(project, "project");
    Preconditions.checkNotNull(project.getBasePath(), "project base path");
    Preconditions.checkNotNull(engine, "engine");
    this.console = console;
    this.globalSettings = globalSettings;
    this.sonarlint = engine;
  }

  @Override
  protected AnalysisResults analyze(Path baseDir, Path workDir, Collection<ClientInputFile> inputFiles, Map<String, String> props,
    IssueListener issueListener, ProgressMonitor progressMonitor) {
    List<RuleKey> excluded = globalSettings.getExcludedRules().stream().map(RuleKey::parse).collect(Collectors.toList());
    List<RuleKey> included = globalSettings.getIncludedRules().stream().map(RuleKey::parse).collect(Collectors.toList());

    StandaloneAnalysisConfiguration config = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFiles(inputFiles)
      .putAllExtraProperties(props)
      .addExcludedRules(excluded)
      .addIncludedRules(included)
      .build();
    console.debug("Starting analysis with configuration:\n" + config.toString());
    return sonarlint.analyze(config, issueListener, new ProjectLogOutput(project, console, projectSettings), progressMonitor);
  }

  @Override
  public Collection<VirtualFile> getExcluded(Module module, Collection<VirtualFile> files, Predicate<VirtualFile> testPredicate) {
    return Collections.emptyList();
  }

  @Override
  public Collection<LoadedAnalyzer> getLoadedAnalyzers() {
    return sonarlint.getLoadedAnalyzers();
  }

  @Override
  public RuleDetails ruleDetails(String ruleKey) {
    return sonarlint.getRuleDetails(ruleKey).orElse(null);
  }

}
