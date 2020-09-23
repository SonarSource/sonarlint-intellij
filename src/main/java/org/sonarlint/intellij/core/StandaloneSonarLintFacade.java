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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.ProjectLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

final class StandaloneSonarLintFacade extends SonarLintFacade {
  private final StandaloneSonarLintEngine sonarlint;

  StandaloneSonarLintFacade(Project project, StandaloneSonarLintEngine engine) {
    super(project);
    Preconditions.checkNotNull(project, "project");
    Preconditions.checkNotNull(project.getBasePath(), "project base path");
    Preconditions.checkNotNull(engine, "engine");
    this.sonarlint = engine;
  }

  @Override
  protected AnalysisResults analyze(Path baseDir, Path workDir, Collection<ClientInputFile> inputFiles, Map<String, String> props,
    IssueListener issueListener, ProgressMonitor progressMonitor) {
    List<RuleKey> excluded = new ArrayList<>();
    List<RuleKey> included = new ArrayList<>();
    Map<RuleKey, Map<String, String>> params = new HashMap<>();
    getGlobalSettings().getRulesByKey().forEach((k, v) -> {
      RuleKey key = RuleKey.parse(k);
      if (v.isActive()) {
        included.add(key);
        params.put(key, v.getParams());
      } else {
        excluded.add(key);
      }
    });

    StandaloneAnalysisConfiguration config = StandaloneAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFiles(inputFiles)
      .putAllExtraProperties(props)
      .addExcludedRules(excluded)
      .addIncludedRules(included)
      .addRuleParameters(params)
      .build();

    SonarLintConsole console = SonarLintUtils.getService(project, SonarLintConsole.class);
    console.debug("Starting analysis with configuration:\n" + config.toString());
    final AnalysisResults analysisResults = sonarlint.analyze(config, issueListener, new ProjectLogOutput(project), progressMonitor);
    AnalysisRequirementNotifications.notifyOnceForSkippedPlugins(analysisResults, sonarlint.getPluginDetails(), project);
    return analysisResults;
  }

  @Override
  public Collection<VirtualFile> getExcluded(Module module, Collection<VirtualFile> files, Predicate<VirtualFile> testPredicate) {
    return Collections.emptyList();
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return sonarlint.getPluginDetails();
  }

  @Override
  public StandaloneRuleDetails getActiveRuleDetails(String ruleKey) {
    return sonarlint.getRuleDetails(ruleKey).orElse(null);
  }

  @Override
  public String getDescription(String ruleKey) {
    StandaloneRuleDetails details = getActiveRuleDetails(ruleKey);
    if (details == null) {
      return null;
    }
    return details.getHtmlDescription();
  }

}
