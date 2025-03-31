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


import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.PathUtil;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.util.FileUtils;

import static com.intellij.openapi.vfs.VirtualFileVisitor.NO_FOLLOW_SYMLINKS;
import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isRider;

public class SonarLintAppUtils {

  private SonarLintAppUtils() {
    // util class
  }

  @CheckForNull
  public static Module findModuleForFile(VirtualFile file, Project project) {
    return computeReadActionSafely(file, project, () -> {
      if (!project.isOpen() || project.isDisposed()) {
        return null;
      }
      return ProjectFileIndex.getInstance(project).getModuleForFile(file, false);
    });
  }

  @CheckForNull
  public static Project guessProjectForFile(VirtualFile file) {
    return ProjectLocator.getInstance().guessProjectForFile(file);
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
    if (isRider()) {
      var relativePathToProject = getPathRelativeToProjectBaseDir(module.getProject(), virtualFile);
      if (relativePathToProject != null) {
        return relativePathToProject;
      }

      var relativePathToModule = getPathRelativeToModuleBaseDir(module, virtualFile);
      if (relativePathToModule != null) {
        return relativePathToModule;
      }
    } else {
      var relativePathToModule = getPathRelativeToModuleBaseDir(module, virtualFile);
      if (relativePathToModule != null) {
        return relativePathToModule;
      }

      var relativePathToProject = getPathRelativeToProjectBaseDir(module.getProject(), virtualFile);
      if (relativePathToProject != null) {
        return relativePathToProject;
      }
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
   *  Path will always contain forward slashes. Resolving the module path uses the official alternative to
   *  {@link com.intellij.openapi.module.Module#getModuleFilePath} which is marked as internally!
   *
   *  INFO: Package level access for the light / heavy testing as manipulating
   *  {@link com.intellij.testFramework.fixtures.BasePlatformTestCase} for
   *  {@link SonarLintAppUtils#getRelativePathForAnalysis} to not return from
   *  {@link SonarLintAppUtils#getPathRelativeToProjectBaseDir} every time would be way too time-consuming!
   */
  @CheckForNull
  static String getPathRelativeToModuleBaseDir(Module module, VirtualFile file) {
    var filePath = Paths.get(file.getPath());
    var moduleContentRoots = Arrays.stream(ModuleRootManager.getInstance(module).getContentRoots())
            .filter(contentRoot -> !contentRoot.getPath().trim().isEmpty())
            .toArray(VirtualFile[]::new);

    // There can be multiple content roots (based on the IntelliJ SDK), e.g. for a Gradle project the project directory
    // and every sourceSet (main, test, ...) or based on the .idea/modules.xml configuration.
    for (VirtualFile contentRoot: moduleContentRoots) {
      var contentRootDir = Paths.get(contentRoot.getPath());
      if (filePath.startsWith(contentRootDir)) {
        return PathUtil.toSystemIndependentName(contentRootDir.relativize(filePath).toString());
      }
    }

    return null;
  }

  public static List<VirtualFile> visitAndAddAllFilesForModule(Module module) {
    var filesToAdd = new ArrayList<VirtualFile>();
    var moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    moduleFileIndex.iterateContent(vFile -> {
      if (module.isDisposed()) {
        return false;
      }

      if (!vFile.isDirectory() && FileUtils.Companion.isFileValidForSonarLint(vFile, module.getProject())) {
        filesToAdd.add(vFile);
        return true;
      }

      return true;
    });
    return filesToAdd;
  }

  public static List<VirtualFile> visitAndAddAllFilesForProject(Project project) {
    var filesToAdd = new ArrayList<VirtualFile>();
    var projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    projectFileIndex.iterateContent(vFile -> {
      if (project.isDisposed()) {
        return false;
      }

      if (!vFile.isDirectory() && FileUtils.Companion.isFileValidForSonarLint(vFile, project)) {
        filesToAdd.add(vFile);
        return true;
      }

      return true;
    });
    return filesToAdd;
  }

  public static List<VirtualFile> visitAndAddAllChildren(VirtualFile file, Project project) {
    var filesToAdd = new ArrayList<VirtualFile>();
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<>(NO_FOLLOW_SYMLINKS) {
      @Override
      public boolean visitFile(VirtualFile file) {
        if (!FileUtils.Companion.isFileValidForSonarLintWithExtensiveChecks(file, project)) {
          return false;
        }

        if (!file.isDirectory()) {
          filesToAdd.add(file);
        }

        return !".git".equals(file.getName());
      }
    });
    return filesToAdd;
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
