/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import com.intellij.openapi.project.Project;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;

@Service(Service.Level.PROJECT)
public final class LocalFileExclusions {

  private Set<String> projectExclusions = new HashSet<>();

  public LocalFileExclusions(Project project) {
    subscribeToSettingsChanges(project);
    loadProjectExclusions(getSettingsFor(project));
  }

  /**
   * Converts a file path to an appropriate glob pattern.
   * - If it's a file: /path/file.txt → ** /path/file.txt
   */
  protected static String fileToGlobPattern(String pathStr) {
    return getNormalized(pathStr);
  }

  /**
   * Converts a directory path to an appropriate glob pattern.
   * - If it's a directory: /path/dir → ** /path/dir/**
   */
  protected static String directoryToGlobPattern(String pathStr) {
    var normalized = getNormalized(pathStr);

    return normalized + "/**";
  }

  private static @NotNull String getNormalized(String pathStr) {
    var normalized = pathStr.replace("\\", "/").replaceAll("/++$", "");
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    normalized = "**" + normalized;
    return normalized;
  }

  private static Set<String> getExclusionsOfType(Collection<ExclusionItem> exclusions, ExclusionItem.Type type) {
    return exclusions.stream()
      .filter(e -> e.type() == type)
      .map(ExclusionItem::item)
      .collect(Collectors.toSet());
  }

  public Set<String> getProjectExclusions() {
    return projectExclusions;
  }

  private void loadProjectExclusions(SonarLintProjectSettings settings) {
    var projectExclusionsItems = settings.getFileExclusions().stream()
      .map(ExclusionItem::parse)
      .filter(Objects::nonNull)
      .toList();

    var projectFileExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.FILE);
    var projectDirExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.DIRECTORY);
    var projectGlobExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.GLOB);

    var normalizedProjectFileExclusions = projectFileExclusions.stream()
            .map(path -> fileToGlobPattern(Paths.get(path).toString()))
            .collect(Collectors.toSet());

    var normalizedProjectDirExclusions = projectDirExclusions.stream()
            .map(path -> directoryToGlobPattern(Paths.get(path).toString()))
            .collect(Collectors.toSet());

    var allExclusions = new HashSet<String>();
    allExclusions.addAll(normalizedProjectFileExclusions);
    allExclusions.addAll(normalizedProjectDirExclusions);
    allExclusions.addAll(projectGlobExclusions);

    projectExclusions = allExclusions;
  }

  private void subscribeToSettingsChanges(Project project) {
    var busConnection = project.getMessageBus().connect();
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, (ProjectConfigurationListener) this::loadProjectExclusions);
  }

}
