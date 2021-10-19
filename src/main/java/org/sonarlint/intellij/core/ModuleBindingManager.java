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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.platform.ModuleAttachProcessor;
import com.intellij.serviceContainer.NonInjectable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;

import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;

import static java.util.Objects.requireNonNull;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ModuleBindingManager {
  private final Module module;
  Supplier<SonarLintEngineManager> engineManagerSupplier;

  public ModuleBindingManager(Module module) {
    this(module, () -> SonarLintUtils.getService(SonarLintEngineManager.class));
  }

  @NonInjectable
  public ModuleBindingManager(Module module, Supplier<SonarLintEngineManager> engineManagerSupplier) {
    this.module = module;
    this.engineManagerSupplier = engineManagerSupplier;
  }

  @CheckForNull
  public ProjectBinding getBinding() {
    String projectKey = resolveProjectKey();
    if (projectKey != null) {
      SonarLintModuleSettings moduleSettings = getSettingsFor(module);
      return new ProjectBinding(projectKey, moduleSettings.getSqPathPrefix(), moduleSettings.getIdePathPrefix());
    }
    return null;
  }

  @CheckForNull
  public String resolveProjectKey() {
    if (isBindingOverrideAllowed()) {
      SonarLintModuleSettings moduleSettings = getSettingsFor(module);
      if (moduleSettings.isProjectBindingOverridden()) {
        return moduleSettings.getProjectKey();
      }
    }
    SonarLintProjectSettings projectSettings = getSettingsFor(module.getProject());
    String defaultProjectKey = projectSettings.getProjectKey();
    if (projectSettings.isBound()) {
      return defaultProjectKey;
    }
    return null;
  }

  /**
   * Module level binding override is allowed if:
   * <li>the IDE supports module or attached projects</li>
   * <li>there is more than one module/at least one attached project</li>
   * <li>the current module is not the primary project</li>
   */
  public boolean isBindingOverrideAllowed() {
    return SonarLintUtils.isModuleLevelBindingEnabled()
            && hasProjectMoreThanOneModule()
            && isNotPrimaryProject();
  }

  /**
   * In some IDEs (PyCharm, PHPStorm, ..) there is the possibility to attach additional projects to the "primary" project.
   * We only want to allow overriding the binding for attached projects.
   */
  private boolean isNotPrimaryProject() {
    return !module.equals(ModuleAttachProcessor.getPrimaryModule(module.getProject()));
  }

  private boolean hasProjectMoreThanOneModule() {
    return ModuleManager.getInstance(module.getProject()).getModules().length > 1;
  }

  public void updatePathPrefixes(ConnectedSonarLintEngine engine) {
    String projectKey = resolveProjectKey();
    if (projectKey == null) {
      throw new IllegalStateException("Project is not bound");
    }
    List<String> moduleFiles = collectPathsForModule();
    ProjectBinding projectBinding = engine.calculatePathPrefixes(projectKey, moduleFiles);
    SonarLintModuleSettings settings = getSettingsFor(module);
    settings.setIdePathPrefix(projectBinding.idePathPrefix());
    settings.setSqPathPrefix(projectBinding.sqPathPrefix());
  }

  private List<String> collectPathsForModule() {
    return ApplicationManager.getApplication().<List<String>>runReadAction(() -> {
      List<String> paths = new ArrayList<>();
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      moduleRootManager.getFileIndex().iterateContent(virtualFile -> {
        if (!virtualFile.isDirectory()) {
          String path = SonarLintAppUtils.getRelativePathForAnalysis(module, virtualFile);
          if (path != null) {
            paths.add(path);
          }
        }
        return true;
      });
      return paths;
    });
  }

  @CheckForNull
  public SonarLintEngine getEngineIfStarted() {
    SonarLintEngineManager engineManager = this.engineManagerSupplier.get();
    SonarLintModuleSettings moduleSettings = getSettingsFor(module);
    SonarLintProjectSettings projectSettings = getSettingsFor(module.getProject());
    if (moduleSettings.isProjectBindingOverridden() || projectSettings.isBound()) {
      String connectionId = projectSettings.getConnectionName();
      return engineManager.getConnectedEngineIfStarted(requireNonNull(connectionId));
    }
    return engineManager.getStandaloneEngineIfStarted();
  }

  public void unbind() {
    getSettingsFor(module).clearBindingOverride();
  }

}
