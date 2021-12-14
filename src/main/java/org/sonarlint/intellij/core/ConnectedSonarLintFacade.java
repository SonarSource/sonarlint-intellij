/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.notifications.AnalysisRequirementNotifications;
import org.sonarlint.intellij.util.ProjectLogOutput;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

class ConnectedSonarLintFacade extends SonarLintFacade {
  private final ConnectedSonarLintEngine engine;
  private final String projectKey;

  ConnectedSonarLintFacade(ConnectedSonarLintEngine engine, Project project, String projectKey) {
    super(project);
    this.projectKey = projectKey;
    Preconditions.checkNotNull(project, "project");
    Preconditions.checkNotNull(project.getBasePath(), "project base path");
    Preconditions.checkNotNull(engine, "engine");
    this.engine = engine;
  }

  @Override
  protected AnalysisResults analyze(Module module, Path baseDir, Path workDir, Collection<ClientInputFile> inputFiles, Map<String, String> props,
    IssueListener issueListener, ClientProgressMonitor progressMonitor) {
    var config = ConnectedAnalysisConfiguration.builder()
      .setBaseDir(baseDir)
      .addInputFiles(inputFiles)
      .setProjectKey(projectKey)
      .putAllExtraProperties(props)
      .setModuleKey(module)
      .build();
    var console = getService(project, SonarLintConsole.class);
    console.debug("Starting analysis with configuration:\n" + config);

    final var analysisResults = engine.analyze(config, issueListener, new ProjectLogOutput(project), progressMonitor);
    AnalysisRequirementNotifications.notifyOnceForSkippedPlugins(analysisResults, engine.getPluginDetails(), project);
    return analysisResults;
  }

  @Override
  public Collection<VirtualFile> getExcluded(Module module, Collection<VirtualFile> files, Predicate<VirtualFile> testPredicate) {
    var bindingManager = getService(module, ModuleBindingManager.class);
    var binding = bindingManager.getBinding();
    if (binding == null) {
      // should never happen since the project should be bound!
      return Collections.emptyList();
    }

    Function<VirtualFile, String> ideFilePathExtractor = s -> SonarLintAppUtils.getRelativePathForAnalysis(module, s);
    return engine.getExcludedFiles(binding, files, ideFilePathExtractor, testPredicate);
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return engine.getPluginDetails();
  }

  @Override
  public CompletableFuture<RuleDescription> getActiveRuleDescription(String ruleKey) {
    try {
      var serverConnection = getService(project, ProjectBindingManager.class).getServerConnection();
      return engine.getActiveRuleDetails(serverConnection.getEndpointParams(), serverConnection.getHttpClient(), ruleKey, projectKey)
        .thenApply(details -> RuleDescription.from(details.getKey(), details.getName(), details.getSeverity(), details.getType(), getFullDescription(details)));
    } catch (InvalidBindingException e) {
      return CompletableFuture.completedFuture(null);
    }
  }

  private static String getFullDescription(ConnectedRuleDetails details) {
    var extendedDescription = details.getExtendedDescription();
    if (extendedDescription.isEmpty()) {
      return details.getHtmlDescription();
    }
    return details.getHtmlDescription() + "<br/><br/>" + extendedDescription;
  }
}
