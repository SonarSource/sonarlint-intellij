/*
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

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.issue.LiveIssue;

public abstract class AbstractScope {
  public static final DataKey<AbstractScope> SCOPE_DATA_KEY = DataKey.create("SonarLintScope");
  private final List<ScopeListener> listeners = new ArrayList<>();

  public abstract String getDisplayName();

  public void addListener(ScopeListener listener) {
    listeners.add(listener);
  }

  public void removeListeners() {
    listeners.clear();
  }

  public abstract String getEmptyText();

  public abstract String getLabelText();

  public abstract String toolbarId();

  @CheckForNull
  public abstract LocalDateTime getLastAnalysisDate();

  public abstract Map<VirtualFile, Collection<LiveIssue>> issues();

  protected void updateIssues() {
    listeners.forEach(ScopeListener::updateIssues);
  }

  protected void updateTexts() {
    listeners.forEach(ScopeListener::updateTexts);
  }

  public interface ScopeListener {
    void updateIssues();

    void updateTexts();
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}
