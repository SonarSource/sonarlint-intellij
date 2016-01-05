/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.ui.scope;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public abstract class IssueTreeScope {
  protected List<ScopeListener> listeners = new ArrayList<>();
  protected Condition<VirtualFile> condition;

  public abstract String getDisplayName();

  public void addListener(ScopeListener listener) {
    listeners.add(listener);
  }

  public void removeListeners() {
    listeners.clear();
  }

  public Condition<VirtualFile> getCondition() {
    return condition;
  }


  public interface ScopeListener {
    void conditionChanged();
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}
