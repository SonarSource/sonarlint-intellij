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
package org.sonarlint.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.common.util.FileUtils;
import org.sonarlint.intellij.finding.TextRangeMatcher;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;

public class ProjectUtils {

  public static Collection<VirtualFile> getAllFiles(Project project) {
    var fileSet = new LinkedHashSet<VirtualFile>();
    var fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    fileIndex.iterateContent(vFile -> {
      if (project.isDisposed()) {
        return false;
      }
      if (!vFile.isDirectory() && vFile.isValid() && FileUtils.Companion.isFileValidForSonarLint(vFile, project)) {
        fileSet.add(vFile);
        return true;
      }
      // Continue iteration
      return true;
    });
    return fileSet;
  }

  public static PsiFile toPsiFile(Project project, VirtualFile file) throws TextRangeMatcher.NoMatchException {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    var psiManager = PsiManager.getInstance(project);
    var psiFile = psiManager.findFile(file);
    if (psiFile != null) {
      return psiFile;
    }
    throw new TextRangeMatcher.NoMatchException("Couldn't find PSI file in module: " + file.getPath());
  }

  public static Map<VirtualFile, Path> getRelativePaths(Project project, Collection<VirtualFile> files) {
    return computeReadActionSafely(project, () -> {
      Map<VirtualFile, Path> relativePathPerFile = new HashMap<>();

      for (var file : files) {
        var relativePath = SonarLintAppUtils.getRelativePathForAnalysis(project, file);
        if (relativePath != null) {
          relativePathPerFile.put(file, Paths.get(relativePath));
        }
      }
      return relativePathPerFile;
    });
  }

  @CheckForNull
  public static VirtualFile tryFindFile(Project project, Path filePath) {
    for (var contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
      if (contentRoot.isDirectory()) {
        var matchedFile = findByRelativePath(contentRoot, filePath);
        if (matchedFile != null) {
          return matchedFile;
        }
      } else {
        // On some version of Rider, all source files are returned as individual content roots, so simply check for equality
        if (contentRoot.getPath().endsWith(getSystemIndependentPath(filePath))) {
          return contentRoot;
        }
      }
    }

    // getContentRoots function does not have consistent behaviour across different version of IDEs,
    // so find the file using root path if it does not work
    var root = ProjectUtil.guessProjectDir(project);
    if (root == null) return null;
    return findByRelativePath(root, filePath);
  }

  @CheckForNull
  private static VirtualFile findByRelativePath(VirtualFile file, Path path) {
    return file.findFileByRelativePath(getSystemIndependentPath(path));
  }

  public static String getSystemIndependentPath(Path filePath) {
    return filePath.toString().replace(File.separatorChar, '/');
  }

  private ProjectUtils() {
    // utility class
  }
}
