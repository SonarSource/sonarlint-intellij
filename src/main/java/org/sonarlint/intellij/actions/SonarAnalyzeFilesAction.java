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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.core.BackendService;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class SonarAnalyzeFilesAction extends AbstractSonarAction {

  public SonarAnalyzeFilesAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public SonarAnalyzeFilesAction() {
    super();
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    var files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return files != null && files.length > 0 && !AbstractSonarAction.isRiderSlnOrCsproj(files);
  }

  @Override
  protected boolean isEnabled(AnActionEvent e, Project project, AnalysisStatus status) {
    var backendIsAlive = getService(BackendService.class).isAlive();
    return !status.isRunning() && backendIsAlive;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    var project = e.getProject();
    var files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

    if (project == null || project.isDisposed() || files == null || files.length == 0) {
      return;
    }

    var hasProject = Stream.of(files)
      .anyMatch(f -> f.getPath().equals(project.getBasePath()));

    if (hasProject && !SonarAnalyzeAllFilesAction.userConfirmed(project)) {
      return;
    }

    var fileSet = Stream.of(files)
      .flatMap(f -> {
        if (f.isDirectory()) {
          var visitor = new CollectFilesVisitor();
          VfsUtilCore.visitChildrenRecursively(f, visitor);
          return visitor.files.stream();
        } else {
          return Stream.of(f);
        }
      })
      .collect(Collectors.toSet());

    runOnPooledThread(project, () -> getService(project, AnalysisSubmitter.class).analyzeFilesOnUserAction(fileSet, e));
  }

  private static class CollectFilesVisitor extends VirtualFileVisitor {
    private final Set<VirtualFile> files = new LinkedHashSet<>();

    public CollectFilesVisitor() {
      super(VirtualFileVisitor.NO_FOLLOW_SYMLINKS);
    }

    @Override
    public boolean visitFile(@NotNull VirtualFile file) {
      var projectFile = ProjectCoreUtil.isProjectOrWorkspaceFile(file, file.getFileType());
      if (!file.isDirectory() && !file.getFileType().isBinary() && !projectFile) {
        files.add(file);
      }
      return !projectFile && !".git".equals(file.getName());
    }
  }
}
