/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ModuleBindingManager {
  private final Module module;

  public ModuleBindingManager(Module module) {
    this.module = module;
  }

  @CheckForNull
  public ProjectBinding getBinding() {
    var projectKey = resolveProjectKey();
    if (projectKey != null) {
      var moduleSettings = getSettingsFor(module);
      return new ProjectBinding(projectKey, moduleSettings.getSqPathPrefix(), moduleSettings.getIdePathPrefix());
    }
    return null;
  }

  @CheckForNull
  public String resolveProjectKey() {
    if (isBindingOverrideAllowed()) {
      var moduleSettings = getSettingsFor(module);
      if (moduleSettings.isProjectBindingOverridden()) {
        return moduleSettings.getProjectKey();
      }
    }
    var projectSettings = getSettingsFor(module.getProject());
    var defaultProjectKey = projectSettings.getProjectKey();
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
    return (SonarLintUtils.isModuleLevelBindingEnabled()
      && hasProjectMoreThanOneModule()
      && isNotPrimaryProject())
      ||
      // If binding was once overridden on a module, we want to keep using it, even if the project is now back to one module
      hasOneModuleAlreadyOverridden();
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

  private boolean hasOneModuleAlreadyOverridden() {
    return Arrays.stream(ModuleManager.getInstance(module.getProject()).getModules()).anyMatch(m -> getSettingsFor(m).isProjectBindingOverridden());
  }

  public void updatePathPrefixes(ConnectedSonarLintEngine engine) {
    var projectKey = resolveProjectKey();
    if (projectKey == null) {
      throw new IllegalStateException("Project is not bound");
    }
    var moduleFiles = collectPathsForModule();
    var projectBinding = engine.calculatePathPrefixes(projectKey, moduleFiles);
    var settings = getSettingsFor(module);
    settings.setIdePathPrefix(projectBinding.idePathPrefix());
    settings.setSqPathPrefix(projectBinding.serverPathPrefix());
  }

  private List<String> collectPathsForModule() {
    return ApplicationManager.getApplication().<List<String>>runReadAction(() -> {
      var paths = new ArrayList<String>();
      var moduleRootManager = ModuleRootManager.getInstance(module);
      moduleRootManager.getFileIndex().iterateContent(virtualFile -> {
        if (!virtualFile.isDirectory()) {
          var path = SonarLintAppUtils.getRelativePathForAnalysis(module, virtualFile);
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
    var engineManager = getService(EngineManager.class);
    var moduleSettings = getSettingsFor(module);
    var projectSettings = getSettingsFor(module.getProject());
    if (moduleSettings.isProjectBindingOverridden() || projectSettings.isBound()) {
      var connectionId = projectSettings.getConnectionName();
      if (connectionId == null) {
        SonarLintConsole.get(module.getProject()).error("Inconsistency in settings, the module or project is bound but there is no connection");
      } else {
        return engineManager.getConnectedEngineIfStarted(connectionId);
      }
    }
    return engineManager.getStandaloneEngineIfStarted();
  }

  public void unbind() {
    getSettingsFor(module).clearBindingOverride();
  }

}
