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
package org.sonarlint.intellij.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettingsStore;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.module.SonarLintModuleSettingsStore;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettingsStore;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class Settings {
  public static SonarLintModuleSettings getSettingsFor(Module module) {
    return getService(module, SonarLintModuleSettingsStore.class).getState();
  }

  public static SonarLintProjectSettings getSettingsFor(Project project) {
    return getService(project, SonarLintProjectSettingsStore.class).getState();
  }

  public static SonarLintGlobalSettings getGlobalSettings() {
    return getService(SonarLintGlobalSettingsStore.class).getState();
  }

  private Settings() {
    // utility class
  }
}
