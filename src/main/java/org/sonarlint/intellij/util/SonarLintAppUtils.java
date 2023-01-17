/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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


import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

public class SonarLintAppUtils {

  private SonarLintAppUtils() {
    // util class
  }

  @CheckForNull
  public static Module findModuleForFile(VirtualFile file, Project project) {
    return getApplication().<Module>runReadAction(() -> {
      if (!project.isOpen()) {
        return null;
      }
      return ProjectFileIndex.getInstance(project).getModuleForFile(file, false);
    });
  }

  @CheckForNull
  public static Project guessProjectForFile(VirtualFile file) {
    return ProjectLocator.getInstance().guessProjectForFile(file);
  }

  public static List<VirtualFile> retainOpenFiles(Project project, List<VirtualFile> files) {
    return getApplication().<List<VirtualFile>>runReadAction(() -> {
      if (!project.isOpen()) {
        return Collections.emptyList();
      }
      return files.stream().filter(f -> FileEditorManager.getInstance(project).isFileOpen(f)).collect(Collectors.toList());
    });
  }

  /**
   * If you already know the module where the file belongs, use {@link #getRelativePathForAnalysis(Module, VirtualFile)}
   * Path will always contain forward slashes.
   */
  @CheckForNull
  public static String getRelativePathForAnalysis(Project project, VirtualFile virtualFile) {
    var module = findModuleForFile(virtualFile, project);
    if (module == null) {
      return null;
    }
    return getRelativePathForAnalysis(module, virtualFile);
  }

  /**
   * Path will always contain forward slashes.
   */
  @CheckForNull
  public static String getRelativePathForAnalysis(Module module, VirtualFile virtualFile) {
    var relativePathToProject = getPathRelativeToProjectBaseDir(module.getProject(), virtualFile);
    if (relativePathToProject != null) {
      return relativePathToProject;
    }

    var relativePathToModule = getPathRelativeToModuleBaseDir(module, virtualFile);
    if (relativePathToModule != null) {
      return relativePathToModule;
    }

    var strictRelativePathToContentRoot = getPathRelativeToContentRoot(module, virtualFile);
    if (strictRelativePathToContentRoot != null) {
      return strictRelativePathToContentRoot;
    }

    return getPathRelativeToCommonAncestorWithProjectBaseDir(module.getProject(), virtualFile);
  }

  @Nullable
  private static String getPathRelativeToCommonAncestorWithProjectBaseDir(Project project, VirtualFile virtualFile) {
    final var projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir == null) {
      return null;
    }
    var commonAncestor = VfsUtilCore.getCommonAncestor(projectDir, virtualFile);
    if (commonAncestor != null) {
      return VfsUtilCore.getRelativePath(virtualFile, commonAncestor);
    }
    return null;
  }

  @CheckForNull
  private static String getPathRelativeToProjectBaseDir(Project project, VirtualFile file) {
    final var projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir == null) {
      return null;
    }
    return VfsUtilCore.getRelativePath(file, projectDir);
  }

  /**
   * Path will always contain forward slashes.
   */
  @CheckForNull
  private static String getPathRelativeToModuleBaseDir(Module module, VirtualFile file) {
    var moduleFilePath = module.getModuleFilePath();
    if ("".equals(moduleFilePath)) {
      // Non persistent module
      return null;
    }
    var baseDir = Paths.get(moduleFilePath).getParent();
    var filePath = Paths.get(file.getPath());
    if (!filePath.startsWith(baseDir)) {
      return null;
    }
    return PathUtil.toSystemIndependentName(baseDir.relativize(filePath).toString());
  }

  @CheckForNull
  private static String getPathRelativeToContentRoot(Module module, VirtualFile file) {
    var moduleRootManager = ModuleRootManager.getInstance(module);
    for (var root : moduleRootManager.getContentRoots()) {
      if (VfsUtilCore.isAncestor(root, file, true)) {
        return VfsUtilCore.getRelativePath(file, root);
      }
    }
    return null;
  }
}
