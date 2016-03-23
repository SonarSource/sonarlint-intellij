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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.ui.SonarLintConsole;

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

  public static String propsToString(Map<String, String> props) {
    StringBuilder builder = new StringBuilder();
    for (Object key : props.keySet()) {
      builder.append(key).append("=").append(props.get(key.toString())).append("\n");
    }
    return builder.toString();
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

  /**
   * FileEditorManager#getSelectedFiles does not work as expected. In split editors, the order of the files does not change depending
   * on which one of the split editors is selected.
   * This seems to work well with split editors.
   */
  @Nullable
  public static VirtualFile getSelectedFile(Project project) {
    FileEditorManager editorManager = FileEditorManager.getInstance(project);
    FileDocumentManager docManager = FileDocumentManager.getInstance();

    Editor editor = editorManager.getSelectedTextEditor();
    if (editor != null) {
      Document doc = editor.getDocument();
      return docManager.getFile(doc);
    }

    return null;
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

  public static boolean shouldAnalyzeAutomatically(@Nullable VirtualFile file, @Nullable Module module) {
    if (!shouldAnalyze(file, module)) {
      return false;
    }

    // file and module not null here
    if ("java".equalsIgnoreCase(file.getFileType().getDefaultExtension()) && !isSource(file, module)) {
      SonarLintConsole.get(module.getProject()).debug("Not automatically analysing java file outside source folder: " + file.getName());
      return false;
    }

    return true;
  }

  public static boolean isSource(VirtualFile file, Module module) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ContentEntry[] entries = moduleRootManager.getContentEntries();
    for (ContentEntry e : entries) {
      SourceFolder[] sourceFolders = e.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        return VfsUtil.isAncestor(sourceFolder.getFile(), file, false);
      }
    }

    return false;

  }

  public static boolean shouldAnalyze(@Nullable VirtualFile file, @Nullable Module module) {
    if (file == null || module == null) {
      return false;
    }

    if (module.isDisposed() || module.getProject().isDisposed()) {
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
