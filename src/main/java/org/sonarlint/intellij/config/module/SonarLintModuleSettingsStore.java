/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.config.module;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.serviceContainer.NonInjectable;

@State(name = "SonarLintModuleSettings", storages = @Storage(StoragePathMacros.MODULE_FILE))
public final class SonarLintModuleSettingsStore implements PersistentStateComponent<SonarLintModuleSettings> {

  private SonarLintModuleSettings settings = new SonarLintModuleSettings();

  public SonarLintModuleSettingsStore() {
  }

  @NonInjectable
  public SonarLintModuleSettingsStore(SonarLintModuleSettings toCopy) {
    loadState(toCopy);
  }

  @Override
  public SonarLintModuleSettings getState() {
    return settings;
  }

  @Override
  public void loadState(SonarLintModuleSettings settings) {
    this.settings = settings;
  }

}
