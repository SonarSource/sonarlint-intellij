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
package org.sonarlint.intellij.util;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class SonarLintAppUtils extends ApplicationComponent.Adapter {
  protected SonarLintAppUtils() {
    super();
  }

  public boolean shouldAnalyzeAutomatically(VirtualFile file, @Nullable Module module) {
    return SonarLintUtils.shouldAnalyzeAutomatically(file, module);
  }

  public boolean shouldAnalyze(VirtualFile file, @Nullable Module module) {
    return SonarLintUtils.shouldAnalyze(file, module);
  }

  @CheckForNull
  public Module findModuleForFile(VirtualFile file, Project project) {
    return ModuleUtil.findModuleForFile(file, project);
  }

  @CheckForNull
  public VirtualFile getSelectedFile(Project project) {
    return SonarLintUtils.getSelectedFile(project);
  }

  @CheckForNull
  public Project guessProjectForFile(VirtualFile file) {
    return ProjectLocator.getInstance().guessProjectForFile(file);
  }

  public boolean isOpenFile(Project project, VirtualFile file) {
    VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();
    return Arrays.stream(openFiles).anyMatch(f -> f.equals(file));
  }
}
