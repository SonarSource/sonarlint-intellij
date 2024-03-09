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
package org.sonarlint.intellij.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.platform.ModuleAttachProcessor;
import java.util.Arrays;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.common.util.SonarLintUtils;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class ModuleBindingManager {
  private final Module module;

  public ModuleBindingManager(Module module) {
    this.module = module;
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

  @CheckForNull
  public String getConfiguredProjectKey() {
    var moduleSettings = getSettingsFor(module);
    if (moduleSettings.isProjectBindingOverridden()) {
      return moduleSettings.getProjectKey();
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

  public void unbind() {
    getSettingsFor(module).clearBindingOverride();
    getService(BackendService.class).moduleUnbound(module);
  }

}
