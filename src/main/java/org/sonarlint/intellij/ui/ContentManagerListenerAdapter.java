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
package org.sonarlint.intellij.ui;

import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

// Before 2020.1, methods were not default in ContentManagerListener
public interface ContentManagerListenerAdapter extends ContentManagerListener {
  @Override
  default void contentAdded(@NotNull ContentManagerEvent event) {
    // default implementation
  }

  @Override
  default void contentRemoved(@NotNull ContentManagerEvent event) {
    // default implementation
  }

  @Override
  default void contentRemoveQuery(@NotNull ContentManagerEvent event) {
    // default implementation
  }

  @Override
  default void selectionChanged(@NotNull ContentManagerEvent event) {
    // default implementation
  }
}
