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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

@State(name = "SonarLintProjectSettings", storages = {@Storage("sonarlint.xml")})
public final class SonarLintProjectSettingsStore implements PersistentStateComponent<SonarLintProjectSettings> {

  private SonarLintProjectSettings settings = new SonarLintProjectSettings();

  /**
   * Constructor called by the XML serialization and deserialization (no args).
   * Even though this class has the scope of a project, we can't have it injected here.
   */
  public SonarLintProjectSettingsStore() {
  }

  /**
   * TODO Replace @Deprecated with @NonInjectable when switching to 2019.3 API level
   * @deprecated in 4.2 to silence a check in 2019.3
   */
  @Deprecated
  public SonarLintProjectSettingsStore(SonarLintProjectSettings toCopy) {
    loadState(toCopy);
  }

  @Override
  public synchronized SonarLintProjectSettings getState() {
    return settings;
  }

  @Override
  public synchronized void loadState(SonarLintProjectSettings settings) {
    this.settings = settings;
  }
}
