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

import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;

public interface EngineManager {
  void stopAllDeletedConnectedEnginesAsync();

  void stopAllEngines(boolean async);

  void stopStandaloneEngine();

  SonarLintAnalysisEngine getStandaloneEngine();

  SonarLintAnalysisEngine getConnectedEngine(SonarLintProjectNotifications notifications, String serverId) throws InvalidBindingException;

  @Nullable SonarLintAnalysisEngine getConnectedEngineIfStarted(String connectionId);

  @Nullable SonarLintAnalysisEngine getStandaloneEngineIfStarted();
}
