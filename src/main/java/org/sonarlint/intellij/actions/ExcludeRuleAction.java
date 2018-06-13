/*
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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.SonarLintDataKeys;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

import java.util.HashSet;
import java.util.Set;

public class ExcludeRuleAction extends DumbAwareAction
{
  @Override
  public void update(AnActionEvent e)
  {
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

    e.getPresentation().setVisible(e.getData(SonarLintDataKeys.SELECTED_ISSUE) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e)
  {
    Project project = e.getProject();
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

    if (project == null || project.isDisposed() || files == null || files.length == 0) {
      return;
    }

    SonarLintProjectSettings settings = SonarLintUtils.get(project, SonarLintProjectSettings.class);
    Set<String> exclusions = new HashSet<>(settings.getRuleExclusions());

    LiveIssue issue = e.getData(SonarLintDataKeys.SELECTED_ISSUE);

    if (issue.getRuleKey().isEmpty()) {
      return;
    }

    exclusions.add(issue.getRuleKey());
    settings.setRuleExclusions(exclusions);

    SonarLintUtils.triggerAnalysis(project, settings);
  }
}
