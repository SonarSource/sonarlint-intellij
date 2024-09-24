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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.fs.VirtualFileEvent;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class EditorOpenTrigger implements FileEditorManagerListener {

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    runOnPooledThread(source.getProject(), () -> {
      var module = SonarLintAppUtils.findModuleForFile(file, source.getProject());
      if (module != null) {
        getService(BackendService.class).updateFileSystem(Map.of(module, List.of(new VirtualFileEvent(ModuleFileEvent.Type.CREATED, file))));
      }
      getService(source.getProject(), AnalysisSubmitter.class).autoAnalyzeFile(file, TriggerType.EDITOR_OPEN);
    });
  }
}
