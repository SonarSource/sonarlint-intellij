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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.util.Set;
import org.sonarlint.intellij.analysis.AnalysisCallback;

public class ShowCurrentFileCallable implements AnalysisCallback {
  private final Project project;

  public ShowCurrentFileCallable(Project project) {
    this.project = project;
  }

  @Override public void onError(Throwable e) {
    // do nothing
  }

  @Override
  public void onSuccess(Set<VirtualFile> failedVirtualFiles) {
    showCurrentFileTab();
  }

  private void showCurrentFileTab() {
    UIUtil.invokeLaterIfNeeded(() -> ServiceManager.getService(project, IssuesViewTabOpener.class).openCurrentFile());
  }
}
