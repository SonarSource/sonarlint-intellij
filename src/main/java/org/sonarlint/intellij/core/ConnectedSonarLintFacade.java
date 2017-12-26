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
package org.sonarlint.intellij.core;

import com.google.common.base.Preconditions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.ProjectLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.FileExclusions;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;

class ConnectedSonarLintFacade extends SonarLintFacade {
  private final ConnectedSonarLintEngine sonarlint;
  private final String moduleKey;
  private final SonarLintConsole console;

  ConnectedSonarLintFacade(ConnectedSonarLintEngine engine, SonarLintProjectSettings projectSettings, SonarLintConsole console, Project project, String moduleKey) {
    super(project, projectSettings);
    Preconditions.checkNotNull(project, "project");
    Preconditions.checkNotNull(project.getBasePath(), "project base path");
    Preconditions.checkNotNull(engine, "engine");
    this.console = console;
    this.sonarlint = engine;
    this.moduleKey = moduleKey;
  }

  @Override
  protected AnalysisResults analyze(Path baseDir, Path workDir, Collection<ClientInputFile> inputFiles, Map<String, String> props,
    IssueListener issueListener, ProgressMonitor progressMonitor) {
    ConnectedAnalysisConfiguration config = new ConnectedAnalysisConfiguration(moduleKey, baseDir, workDir, inputFiles, props);
    console.debug("Starting analysis with configuration:\n" + config.toString());

    return sonarlint.analyze(config, issueListener, new ProjectLogOutput(console, projectSettings), progressMonitor);
  }

  @Override
  public Collection<VirtualFile> getExcluded(Collection<VirtualFile> files, Predicate<VirtualFile> testPredicate) {
    Map<String, VirtualFile> fileByPath = files.stream()
      .collect(Collectors.toMap(f-> SonarLintUtils.getRelativePath(project, f), x->x, (x, y) -> x));

    Set<String> excludedFiles = sonarlint.getExcludedFiles(moduleKey, fileByPath.keySet(), p -> testPredicate.test(fileByPath.get(p)));
    return excludedFiles.stream().map(fileByPath::get).collect(Collectors.toList());
  }

  @Override protected RuleDetails ruleDetails(String ruleKey) {
    return sonarlint.getRuleDetails(ruleKey);
  }

  @Override
  public boolean requiresSavingFiles() {
    return !sonarlint.getLoadedAnalyzers().stream().allMatch(LoadedAnalyzer::supportsContentStream);
  }
}
