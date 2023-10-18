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
package org.sonarlint.intellij.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class ExcludeFileAction extends AbstractSonarAction {
  public ExcludeFileAction() {

  }

  public ExcludeFileAction(String text) {
    super(text, null, AllIcons.Actions.Cancel);
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    var files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return ActionPlaces.isPopupPlace(e.getPlace()) && files != null && files.length > 0 && !AbstractSonarAction.isRiderSlnOrCsproj(files);
  }

  @Override
  protected boolean isEnabled(AnActionEvent e, Project project, AnalysisStatus status) {
    var files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    var exclusions = List.copyOf(getSettingsFor(project).getFileExclusions());

    return files != null && toStringStream(project, files)
      .anyMatch(path -> !exclusions.contains(path));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    var project = e.getProject();
    var files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

    if (project == null || project.isDisposed() || files == null || files.length == 0) {
      return;
    }

    runOnPooledThread(project, () -> {
      var settings = getSettingsFor(project);
      var exclusions = new ArrayList<>(settings.getFileExclusions());

      var newExclusions = toStringStream(project, files)
        .filter(path -> !exclusions.contains(path))
        .toList();
      if (!newExclusions.isEmpty()) {
        exclusions.addAll(newExclusions);
        settings.setFileExclusions(exclusions);
        var projectListener = project.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);
        projectListener.changed(settings);
        SonarLintUtils.getService(project, AnalysisSubmitter.class).autoAnalyzeOpenFiles(TriggerType.CONFIG_CHANGE);
      }
    });
  }

  private static Stream<String> toStringStream(Project project, VirtualFile[] files) {
    return Stream.of(files)
      .map(vf -> toExclusion(project, vf))
      .filter(Objects::nonNull)
      .filter(exclusion -> !exclusion.item().isEmpty())
      .map(ExclusionItem::toStringWithType);
  }

  @CheckForNull
  private static ExclusionItem toExclusion(Project project, VirtualFile virtualFile) {
    var relativeFilePath = SonarLintAppUtils.getRelativePathForAnalysis(project, virtualFile);
    if (relativeFilePath == null) {
      return null;
    }
    if (virtualFile.isDirectory()) {
      return new ExclusionItem(ExclusionItem.Type.DIRECTORY, relativeFilePath);
    } else {
      return new ExclusionItem(ExclusionItem.Type.FILE, relativeFilePath);
    }
  }
}
