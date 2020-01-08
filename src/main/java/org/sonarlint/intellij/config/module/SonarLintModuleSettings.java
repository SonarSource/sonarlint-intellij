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
package org.sonarlint.intellij.config.module;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.util.AbstractModuleComponent;

@State(name = "SonarLintModuleSettings", storages = @Storage(StoragePathMacros.MODULE_FILE))
public final class SonarLintModuleSettings extends AbstractModuleComponent implements PersistentStateComponent<SonarLintModuleSettings> {
  private String sqPathPrefix = "";
  private String idePathPrefix = "";

  public SonarLintModuleSettings() {
  }

  /**
   * TODO Replace @Deprecated with @NonInjectable when switching to 2019.3 API level
   * @deprecated in 4.2 to silence a check in 2019.3
   */
  @Deprecated
  public SonarLintModuleSettings(SonarLintModuleSettings toCopy) {
    XmlSerializerUtil.copyBean(toCopy, this);
  }

  @Nullable
  @Override
  public SonarLintModuleSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull SonarLintModuleSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public String getSqPathPrefix() {
    return sqPathPrefix;
  }

  public void setSqPathPrefix(String sqPathPrefix) {
    this.sqPathPrefix = sqPathPrefix;
  }

  public String getIdePathPrefix() {
    return idePathPrefix;
  }

  public void setIdePathPrefix(String idePathPrefix) {
    this.idePathPrefix = idePathPrefix;
  }
}
