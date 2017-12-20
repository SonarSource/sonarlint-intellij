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
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.util.SonarLintUtils;

public class ExcludeAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    e.getPresentation().setVisible(true);

    if (project == null || !project.isInitialized() || project.isDisposed()) {
      e.getPresentation().setEnabled(false);
    }

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (!ActionPlaces.isPopupPlace(e.getPlace()) || files == null || files.length == 0) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    }

    SonarLintProjectSettings settings = SonarLintUtils.get(project, SonarLintProjectSettings.class);
    List<String> exclusions = new ArrayList<>(settings.getFileExclusions());

    boolean anyFileToAdd = Arrays.stream(files)
      .map(vf -> SonarLintUtils.getRelativePath(project, vf))
      .anyMatch(path -> !exclusions.contains(path));

    if (!anyFileToAdd) {
      e.getPresentation().setEnabled(false);
    }
  }

  @Override public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

    if (project == null || files == null || files.length == 0) {
      return;
    }

    SonarLintProjectSettings settings = SonarLintUtils.get(project, SonarLintProjectSettings.class);
    List<String> exclusions = new ArrayList<>(settings.getFileExclusions());

    Arrays.stream(files)
      .map(vf -> SonarLintUtils.getRelativePath(project, vf))
      .filter(path -> !exclusions.contains(path))
      .forEach(exclusions::add);

    settings.setFileExclusions(exclusions);
    // TODO trigger update
  }

}
