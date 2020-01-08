/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import icons.SonarLintIcons;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarAnalyzeFilesAction extends DumbAwareAction {
  public SonarAnalyzeFilesAction() {
    super();
  }

  public SonarAnalyzeFilesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null || files.length == 0) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
      return;
    }

    if (SonarLintToolWindowFactory.TOOL_WINDOW_ID.equals(e.getPlace())) {
      e.getPresentation().setIcon(SonarLintIcons.PLAY);
    }
    e.getPresentation().setVisible(true);

    Project project = e.getProject();
    if (project == null || !project.isInitialized() || project.isDisposed()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    SonarLintStatus status = SonarLintUtils.get(project, SonarLintStatus.class);
    if (status.isRunning()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

    if (project == null || project.isDisposed() || files == null || files.length == 0) {
      return;
    }

    boolean hasProject = Arrays.stream(files)
      .anyMatch(f -> f.getPath().equals(project.getBasePath()));

    if (hasProject && !SonarAnalyzeAllFilesAction.showWarning()) {
      return;
    }

    List<VirtualFile> fileList = Arrays.stream(files)
      .flatMap(f -> {
        if (f.isDirectory()) {
          CollectFilesVisitor visitor = new CollectFilesVisitor();
          VfsUtil.visitChildrenRecursively(f, visitor);
          return visitor.files.stream();
        } else {
          return Stream.of(f);
        }
      })
      .distinct()
      .collect(Collectors.toList());

    SonarLintSubmitter submitter = SonarLintUtils.get(project, SonarLintSubmitter.class);
    AnalysisCallback callback;

    if (SonarLintToolWindowFactory.TOOL_WINDOW_ID.equals(e.getPlace())) {
      callback = new ShowCurrentFileCallable(project);
    } else {
      callback = new ShowAnalysisResultsCallable(project, fileList, whatAnalyzed(fileList.size()));
    }

    submitter.submitFiles(fileList, TriggerType.ACTION, callback, executeBackground(e));
  }

  private static String whatAnalyzed(int numFiles) {
    if (numFiles == 1) {
      return "1 file";
    } else {
      return numFiles + " files";
    }
  }

  private static class CollectFilesVisitor extends VirtualFileVisitor {
    private List<VirtualFile> files = new ArrayList<>();

    public CollectFilesVisitor() {
      super(VirtualFileVisitor.NO_FOLLOW_SYMLINKS);
    }

    @Override
    public boolean visitFile(@NotNull VirtualFile file) {
      boolean projectFile = ProjectCoreUtil.isProjectOrWorkspaceFile(file, file.getFileType());
      if (!file.isDirectory() && !file.getFileType().isBinary() && !projectFile) {
        files.add(file);
      }
      return !projectFile && !".git".equals(file.getName());
    }
  }

  /**
   * Whether the analysis should be launched in the background.
   * Analysis should be run in background in the following cases:
   * - Keybinding used (place = MainMenu)
   * - Macro used (place = unknown)
   * - Action used, ctrl+shift+A (place = GoToAction)
   */
  private static boolean executeBackground(AnActionEvent e) {
    return ActionPlaces.isMainMenuOrActionSearch(e.getPlace())
      || ActionPlaces.UNKNOWN.equals(e.getPlace());
  }
}
