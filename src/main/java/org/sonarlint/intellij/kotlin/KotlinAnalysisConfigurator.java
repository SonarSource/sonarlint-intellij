/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.kotlin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;

public class KotlinAnalysisConfigurator implements AnalysisConfigurator {

  private static final String KOTLIN_GRADLE_PROJECT_ROOT = "sonar.kotlin.gradleProjectRoot";

  @Override
  public AnalysisConfiguration configure(@NotNull Module module, Collection<VirtualFile> filesToAnalyze) {
    var config = new AnalysisConfiguration();
    var properties = config.extraProperties;
    var file = filesToAnalyze.stream().findFirst();
    if (file.isPresent()) {
      var gradleProjectRootFinder = findGradleProjectRoot(file.get());

      if (gradleProjectRootFinder != null) {
        properties.put(KOTLIN_GRADLE_PROJECT_ROOT, gradleProjectRootFinder.getPath());
      }
    }
    return config;
  }

  @Nullable
  private VirtualFile findGradleProjectRoot(@NotNull VirtualFile sourceFile) {
    VirtualFile parent = sourceFile.getParent();
    while (parent != null) {
      if (isGradleProjectDir(parent)) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  private boolean isGradleProjectDir(@NotNull VirtualFile file) {
    return containsChild(file,
      child -> {
        if (!child.isDirectory()) {
          String name = child.getName();
          return "settings.gradle".equals(name) ||
            "settings.gradle.kts".equals(name) ||
            "build.gradle".equals(name) ||
            "build.gradle.kts".equals(name);
        }
        return false;
      });
  }

  private boolean containsChild(@NotNull VirtualFile file, @NotNull Predicate<? super VirtualFile> predicate) {
    if (file.isDirectory()) {
      for (VirtualFile child : file.getChildren()) {
        if (predicate.test(child))
          return true;
      }
    }
    return false;
  }

}
