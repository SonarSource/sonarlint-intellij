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
package org.sonarlint.intellij.util;

import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;

public interface GlobalLogOutput extends ClientLogOutput {
  static GlobalLogOutput get() {
    return SonarLintUtils.getService(GlobalLogOutput.class);
  }

  void log(String msg, ClientLogOutput.Level level);

  void logError(String msg, @Nullable Throwable t);

}
