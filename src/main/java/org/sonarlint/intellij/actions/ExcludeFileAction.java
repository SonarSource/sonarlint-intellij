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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

public class ExcludeFileAction extends DumbAwareAction {
  public ExcludeFileAction() {

  }

  public ExcludeFileAction(String text) {
    super(text, null, AllIcons.Actions.Cancel);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Project project = e.getProject();
    if (project == null || !project.isInitialized() || project.isDisposed()) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(true);
      return;
    }

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (!ActionPlaces.isPopupPlace(e.getPlace()) || files == null || files.length == 0) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    SonarLintProjectSettings settings = SonarLintUtils.get(project, SonarLintProjectSettings.class);
    List<String> exclusions = new ArrayList<>(settings.getFileExclusions());

    boolean anyFileToAdd = toStringStream(project, files)
      .anyMatch(path -> !exclusions.contains(path));

    if (!anyFileToAdd) {
      e.getPresentation().setEnabled(false);
    }
  }

  @Override public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

    if (project == null || project.isDisposed() || files == null || files.length == 0) {
      return;
    }

    SonarLintProjectSettings settings = SonarLintUtils.get(project, SonarLintProjectSettings.class);
    List<String> exclusions = new ArrayList<>(settings.getFileExclusions());

    List<String> newExclusions = toStringStream(project, files)
      .filter(path -> !exclusions.contains(path))
      .collect(Collectors.toList());

    if (!newExclusions.isEmpty()) {
      exclusions.addAll(newExclusions);
      settings.setFileExclusions(exclusions);
      ProjectConfigurationListener projectListener = project.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);
      projectListener.changed(settings);
      SonarLintUtils.get(project, SonarLintSubmitter.class).submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    }
  }

  private static Stream<String> toStringStream(Project project, VirtualFile[] files) {
    SonarLintAppUtils appUtils = SonarLintUtils.get(SonarLintAppUtils.class);
    return Arrays.stream(files)
      .map(vf -> toExclusion(appUtils, project, vf))
      .filter(Objects::nonNull)
      .filter(exclusion -> !exclusion.item().isEmpty())
      .map(ExclusionItem::toStringWithType);
  }

  @CheckForNull
  private static ExclusionItem toExclusion(SonarLintAppUtils appUtils, Project project, VirtualFile virtualFile) {
    String relativeFilePath = appUtils.getRelativePathForAnalysis(project, virtualFile);
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
