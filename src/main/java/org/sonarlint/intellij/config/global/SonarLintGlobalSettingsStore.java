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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.credentials.CredentialsService;

import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

@State(name = "SonarLintGlobalSettings",
  storages = {@Storage("sonarlint.xml")},
  // used for settings export
  presentableName = SonarLintGlobalSettingsPresentableName.class
)
public final class SonarLintGlobalSettingsStore implements PersistentStateComponent<SonarLintGlobalSettings> {

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

  public void save(SonarLintGlobalSettings settings) {
    this.settings = settings;
  }

  private void initializeRulesByKey() {
    settings.rulesByKey = new HashMap<>(settings.rules.stream().collect(Collectors.toMap(SonarLintGlobalSettings.Rule::getKey, Function.identity())));
  }
}
