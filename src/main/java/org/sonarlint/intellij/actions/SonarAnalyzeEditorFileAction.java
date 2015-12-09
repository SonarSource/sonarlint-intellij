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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.analysis.SonarlintAnalyzer;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

import javax.annotation.Nullable;
import java.util.Collections;

public class SonarAnalyzeEditorFileAction extends AbstractSonarAction {
  @Override
  protected boolean isEnabled(SonarLintStatus status) {
    return !status.isRunning();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    //VirtualFile[] files = DataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    Project p = e.getProject();
    VirtualFile[] selectedFiles = FileEditorManager.getInstance(p).getSelectedFiles();
    SonarLintConsole console = SonarLintConsole.getSonarQubeConsole(p);

    if (selectedFiles.length == 0) {
      console.error("No files for analysis");
      return;
    }

    Module m = ModuleUtil.findModuleForFile(selectedFiles[0], p);

    if (shouldAnalyze(selectedFiles[0], m)) {
      boolean background = ActionPlaces.isMainMenuOrActionSearch(e.getPlace());
      SonarlintAnalyzer analyzer = p.getComponent(SonarlintAnalyzer.class);
      if(background) {
        analyzer.submitAsync(m, Collections.singletonList(selectedFiles[0]));
      } else {
        analyzer.submit(m, Collections.singletonList(selectedFiles[0]));
      }
    } else {
      console.error("File " + selectedFiles[0] + " cannot be analyzed");
    }
  }

  private static boolean shouldAnalyze(@Nullable VirtualFile file, Module module) {
    if (file == null) {
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

    if (module == null) {
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
