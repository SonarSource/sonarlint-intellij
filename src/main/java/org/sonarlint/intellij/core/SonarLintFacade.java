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

import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.atomic.AtomicInteger;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;

import javax.annotation.CheckForNull;

public abstract class SonarLintFacade {
  protected final Project project;
  protected final SonarLintProjectSettings projectSettings;

  protected SonarLintFacade(Project project, SonarLintProjectSettings projectSettings) {
    this.project = project;
    this.projectSettings = projectSettings;
  }

  protected abstract AnalysisResults analyse(Path baseDir, Path workDir, Collection<ClientInputFile> inputFiles, Map<String, String> props, IssueListener issueListener);
  protected abstract RuleDetails ruleDetails(String ruleKey);

  public synchronized AnalysisResults startAnalysis(List<ClientInputFile> inputFiles, IssueListener issueListener, Map<String, String> additionalProps) {
    Path baseDir = Paths.get(project.getBasePath());
    Path workDir = baseDir.resolve(Project.DIRECTORY_STORE_FOLDER).resolve("sonarlint").toAbsolutePath();
    Map<String, String> props = new HashMap<>();
    props.putAll(additionalProps);
    props.putAll(projectSettings.getAdditionalProperties());
    return analyse(baseDir, workDir, inputFiles, props, issueListener);
  }

  @CheckForNull
  public String getDescription(String ruleKey) {
      RuleDetails details = ruleDetails(ruleKey);
      if (details == null) {
        return null;
      }
      if (details.getExtendedDescription().isEmpty()) {
        return details.getHtmlDescription();
      }
      return details.getHtmlDescription() + "<br/><br/>" + details.getExtendedDescription();
  }

  @CheckForNull
  public String getRuleName(String ruleKey) {
    RuleDetails details = ruleDetails(ruleKey);
    if (details == null) {
      return null;
    }
    return details.getName();
  }
}
