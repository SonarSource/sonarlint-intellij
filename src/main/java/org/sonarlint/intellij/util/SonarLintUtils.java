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
package org.sonarlint.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;

public class SonarLintUtils {

  private static final Logger LOG = Logger.getInstance(SonarLintUtils.class);

  private SonarLintUtils() {
    // Utility class
  }

  public static String getModuleRootPath(Module module) {
    VirtualFile moduleRoot = getModuleRoot(module);
    return moduleRoot.getPath();
  }

  public static VirtualFile getModuleRoot(Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    VirtualFile[] contentRoots = rootManager.getContentRoots();
    if (contentRoots.length != 1) {
      LOG.error("Module " + module + " contains " + contentRoots.length + " content roots and this is not supported");
      throw new IllegalStateException("No basedir for module " + module);
    }
    return contentRoots[0];
  }

  public static boolean saveFiles(final Collection<VirtualFile> virtualFiles) {
    boolean success = true;
    for (VirtualFile file : virtualFiles) {
      if (!saveFile(file)) {
        success = false;
      }
    }
    return success;
  }

  public static boolean saveFile(@NotNull final VirtualFile virtualFile) {
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    if (fileDocumentManager.isFileModified(virtualFile)) {
      final Document document = fileDocumentManager.getDocument(virtualFile);
      if (document != null) {
        ApplicationManager.getApplication().invokeAndWait(
          new Runnable() {
            @Override
            public void run() {
              fileDocumentManager.saveDocument(document);
            }
          }, ModalityState.any()
        );
        return true;
      }
    }
    return false;
  }

  public static boolean shouldAnalyze(@Nullable VirtualFile file, @Nullable Module module) {
    if (file == null || module == null) {
      return false;
    }

    if (module.getProject().isDisposed()) {
      return false;
    }

    if (!file.isInLocalFileSystem() || file.getFileType().isBinary() || !file.isValid()
      || ".idea".equals(file.getParent().getName())) {
      return false;
    }

    // In PHPStorm the same PHP file is analyzed twice (once as PHP file and once as HTML file)
    if ("html".equalsIgnoreCase(file.getFileType().getName())) {
      return false;
    }

    final VirtualFile baseDir = SonarLintUtils.getModuleRoot(module);

    if (baseDir == null) {
      throw new IllegalStateException("No basedir for module " + module);
    }

    String baseDirPath = baseDir.getCanonicalPath();
    if (baseDirPath == null) {
      throw new IllegalStateException("No basedir path for module " + module);
    }

    return true;
  }

}
