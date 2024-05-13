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
package org.sonarlint.intellij.mediumtests.fixtures;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;


public class ProjectStorageFixture {

  public record ProjectStorage(Path path) {

    public void setSettings(Map<String, String> settings) {
      var configFile = path.resolve("analyzer_config.pb");
      var analyzerConfiguration = ProtobufFileUtil.readFile(configFile, Sonarlint.AnalyzerConfiguration.parser());
      ProtobufFileUtil.writeToFile(Sonarlint.AnalyzerConfiguration.newBuilder(analyzerConfiguration)
        .clearSettings()
        .putAllSettings(settings).build(), configFile);
    }

  }

  public static class ProjectStorageBuilder {
    private final String projectKey;
    private final List<RuleSetBuilder> ruleSets = new ArrayList<>();
    private String mainBranchName;
    private final Set<String> branchNames = new HashSet<>();

    public ProjectStorageBuilder(String projectKey) {
      this.projectKey = projectKey;
    }

    public ProjectStorageBuilder withMainBranchName(String mainBranchName) {
      this.mainBranchName = mainBranchName;
      branchNames.add(mainBranchName);
      return this;
    }

    public ProjectStorageBuilder withRuleSet(String languageKey, Consumer<RuleSetBuilder> consumer) {
      var ruleSetBuilder = new RuleSetBuilder(languageKey);
      consumer.accept(ruleSetBuilder);
      ruleSets.add(ruleSetBuilder);
      return this;
    }

    public ProjectStorage create(Path projectsRootPath) {
      var projectFolder = projectsRootPath.resolve(ProjectStoragePaths.encodeForFs(projectKey));
      try {
        FileUtils.forceMkdir(projectFolder.toFile());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }

      createAnalyzerConfiguration(projectFolder);
      if (mainBranchName != null) {
        createProjectBranches(projectFolder);
      }
      return new ProjectStorage(projectFolder);
    }

    private void createAnalyzerConfiguration(Path projectFolder) {
      Map<String, Sonarlint.RuleSet> protoRuleSets = new HashMap<>();
      ruleSets.forEach(ruleSet -> {
        var ruleSetBuilder = Sonarlint.RuleSet.newBuilder();
        ruleSet.activeRules.forEach(activeRule -> {
          ruleSetBuilder.addRule(Sonarlint.RuleSet.ActiveRule.newBuilder()
            .setRuleKey(activeRule.ruleKey)
            .setSeverity(activeRule.severity)
            .setTemplateKey(trimToEmpty(activeRule.templateKey))
            .putAllParams(activeRule.params)
            .build());
        });
        protoRuleSets.put(ruleSet.languageKey, ruleSetBuilder.build());
      });
      var analyzerConfiguration = Sonarlint.AnalyzerConfiguration.newBuilder().putAllRuleSetsByLanguageKey(protoRuleSets).build();
      ProtobufFileUtil.writeToFile(analyzerConfiguration, projectFolder.resolve("analyzer_config.pb"));
    }

    private void createProjectBranches(Path projectFolder) {
      var builder = Sonarlint.ProjectBranches.newBuilder()
        .setMainBranchName(mainBranchName)
        .addAllBranchName(branchNames);
      ProtobufFileUtil.writeToFile(builder.build(), projectFolder.resolve("project_branches.pb"));
    }

    public static class RuleSetBuilder {
      private final String languageKey;
      private final List<ActiveRule> activeRules = new ArrayList<>();

      public RuleSetBuilder(String languageKey) {
        this.languageKey = languageKey;
      }

      public RuleSetBuilder withActiveRule(String ruleKey, String severity) {
        return withActiveRule(ruleKey, severity, Map.of());
      }

      public RuleSetBuilder withActiveRule(String ruleKey, String severity, Map<String, String> params) {
        activeRules.add(new ActiveRule(ruleKey, severity, null, params));
        return this;
      }
    }

    private static class ActiveRule {
      private final String ruleKey;
      private final String severity;
      private final String templateKey;
      private final Map<String, String> params;

      private ActiveRule(String ruleKey, String severity, @Nullable String templateKey, Map<String, String> params) {
        this.ruleKey = ruleKey;
        this.severity = severity;
        this.templateKey = templateKey;
        this.params = params;
      }
    }
  }
}
