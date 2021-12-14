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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public abstract class SonarLintFacade {
  protected final Project project;

  protected SonarLintFacade(Project project) {
    this.project = project;
  }

  protected abstract AnalysisResults analyze(Module module, Path baseDir, Path workDir, Collection<ClientInputFile> inputFiles, Map<String, String> props,
    IssueListener issueListener, ClientProgressMonitor progressMonitor);

  public abstract CompletableFuture<RuleDescription> getActiveRuleDescription(String ruleKey);

  public synchronized AnalysisResults startAnalysis(Module module, List<ClientInputFile> inputFiles, IssueListener issueListener,
                                                    Map<String, String> additionalProps, ClientProgressMonitor progressMonitor) {
    var baseDir = Paths.get(project.getBasePath());
    var workDir = baseDir.resolve(Project.DIRECTORY_STORE_FOLDER).resolve("sonarlint").toAbsolutePath();
    var props = new HashMap<String, String>();
    props.putAll(additionalProps);
    props.putAll(getSettingsFor(project).getAdditionalProperties());
    return analyze(module, baseDir, workDir, inputFiles, props, issueListener, progressMonitor);
  }

  public abstract Collection<VirtualFile> getExcluded(Module module, Collection<VirtualFile> files, Predicate<VirtualFile> testPredicate);

  public abstract Collection<PluginDetails> getPluginDetails();
}
