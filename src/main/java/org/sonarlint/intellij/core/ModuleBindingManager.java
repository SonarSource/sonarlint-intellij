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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;

public class ModuleBindingManager {
  private final Module module;

  public ModuleBindingManager(Module module) {
    this.module = module;
  }

  @CheckForNull
  public ProjectBinding getBinding() {
    String projectKey = SonarLintUtils.getService(module.getProject(), SonarLintProjectSettings.class).getProjectKey();
    if (projectKey == null) {
      return null;
    }
    SonarLintModuleSettings settings = SonarLintUtils.getService(module, SonarLintModuleSettings.class);
    return new ProjectBinding(projectKey, settings.getSqPathPrefix(), settings.getIdePathPrefix());
  }

  public void updateBinding(ConnectedSonarLintEngine engine) {
    String projectKey = SonarLintUtils.getService(module.getProject(), SonarLintProjectSettings.class).getProjectKey();
    if (projectKey == null) {
      throw new IllegalStateException("Project is not bound");
    }
    List<String> moduleFiles = collectPathsForModule();
    ProjectBinding projectBinding = engine.calculatePathPrefixes(projectKey, moduleFiles);
    SonarLintModuleSettings settings = SonarLintUtils.getService(module, SonarLintModuleSettings.class);
    settings.setIdePathPrefix(projectBinding.idePathPrefix());
    settings.setSqPathPrefix(projectBinding.sqPathPrefix());
  }

  private List<String> collectPathsForModule() {
    return ApplicationManager.getApplication().<List<String>>runReadAction(() -> {
      List<String> paths = new ArrayList<>();
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      moduleRootManager.getFileIndex().iterateContent(virtualFile -> {
        if (!virtualFile.isDirectory()) {
          String path = SonarLintAppUtils.getPathRelativeToProjectBaseDir(module.getProject(), virtualFile);
          if (path != null) {
            paths.add(path);
          }
        }
        return true;
      });
      return paths;
    });
  }
}
