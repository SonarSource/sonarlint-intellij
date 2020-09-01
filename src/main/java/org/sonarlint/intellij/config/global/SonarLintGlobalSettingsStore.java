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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.util.SonarLintBundle;

import java.io.File;
import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@State(name = "SonarLintGlobalSettings", storages = {@Storage("sonarlint.xml")})
public final class SonarLintGlobalSettingsStore implements PersistentStateComponent<SonarLintGlobalSettings>, ExportableApplicationComponent {

  private SonarLintGlobalSettings settings = new SonarLintGlobalSettings();

  @Override
  public SonarLintGlobalSettings getState() {
    this.settings.rules = this.settings.rulesByKey.values();
    return settings;
  }

  @Override
  public void loadState(SonarLintGlobalSettings settings) {
    this.settings = settings;
    initializeRulesByKey();
  }

  private void initializeRulesByKey() {
    settings.rulesByKey = new HashMap<>(settings.rules.stream().collect(Collectors.toMap(SonarLintGlobalSettings.Rule::getKey, Function.identity())));
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[] {PathManager.getOptionsFile("sonarlint")};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return SonarLintBundle.message("sonarlint.settings");
  }

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "SonarLintGlobalSettings";
  }
}
