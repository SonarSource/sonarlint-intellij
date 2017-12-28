package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.util.SonarLintUtils;

public class ExcludeFileAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    e.getPresentation().setVisible(true);

    if (project == null || !project.isInitialized() || project.isDisposed()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (!ActionPlaces.isPopupPlace(e.getPlace()) || files == null || files.length == 0) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
      return;
    }

    SonarLintProjectSettings settings = SonarLintUtils.get(project, SonarLintProjectSettings.class);
    List<String> exclusions = new ArrayList<>(settings.getFileExclusions());

    boolean anyFileToAdd = Arrays.stream(files)
      .map(vf -> toExclusion(project, vf))
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

    List<String> newExclusions = Arrays.stream(files)
      .map(vf -> toExclusion(project, vf))
      .filter(path -> !exclusions.contains(path))
      .collect(Collectors.toList());

    if (!newExclusions.isEmpty()) {
      exclusions.addAll(newExclusions);
      settings.setFileExclusions(exclusions);
      ProjectConfigurationListener projectListener = project.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);
      projectListener.changed(settings);
    }
  }

  private String toExclusion(Project project, VirtualFile virtualFile) {
    String filePath = SonarLintUtils.getRelativePath(project, virtualFile);
    if (virtualFile.isDirectory()) {
      return new ExclusionItem(ExclusionItem.Type.DIRECTORY, filePath).toStringWithType();
    } else {
      return new ExclusionItem(ExclusionItem.Type.FILE, filePath).toStringWithType();
    }
  }
}
