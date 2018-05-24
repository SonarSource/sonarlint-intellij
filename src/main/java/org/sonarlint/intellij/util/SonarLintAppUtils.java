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
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import javax.annotation.CheckForNull;

public class SonarLintAppUtils extends ApplicationComponent.Adapter {
  protected SonarLintAppUtils() {
    super();
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

  /**
   * If you already know the module where the file belongs, use {@link #getRelativePathForAnalysis(Module, VirtualFile)}
   * Path will always contain forward slashes.
   */
  @CheckForNull
  public String getRelativePathForAnalysis(Project project, VirtualFile virtualFile) {
    Module module = findModuleForFile(virtualFile, project);
    if (module == null) {
      return null;
    }
    return getRelativePathForAnalysis(module, virtualFile);
  }

  /**
   * Path will always contain forward slashes.
   */
  @CheckForNull
  public String getRelativePathForAnalysis(Module module, VirtualFile virtualFile) {
    if (module.getProject().getBasePath() != null) {
      String relativePathToProject = VfsUtil.getRelativePath(virtualFile, module.getProject().getBaseDir());
      if (relativePathToProject != null) {
        return relativePathToProject;
      }
    }

    String relativePathToModule = getPathRelativeToModuleBaseDir(module, virtualFile);
    if (relativePathToModule != null) {
      return relativePathToModule;
    }

    return getPathRelativeToContentRoot(module, virtualFile);
  }

  @CheckForNull
  public String getPathRelativeToModuleBaseDir(Module module, VirtualFile file) {
    VirtualFile moduleFile = module.getModuleFile();
    return moduleFile == null ? null : VfsUtil.getRelativePath(file, moduleFile.getParent());
  }

  @CheckForNull
  public String getPathRelativeToContentRoot(Module module, VirtualFile file) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    for (VirtualFile root : moduleRootManager.getContentRoots()) {
      if (VfsUtil.isAncestor(root, file, true)) {
        return VfsUtil.getRelativePath(file, root);
      }
    }
    return null;
  }
}
