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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.time.LocalDateTime;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.util.ResourceLoader;
import org.sonarlint.intellij.util.SonarLintUtils;

public class LastAnalysisPanel implements Disposable {
  private static final Logger LOGGER = Logger.getInstance(LastAnalysisPanel.class);
  private static final String NO_ANALYSIS_LABEL = "Trigger the analysis to find issues on the files in the VCS change set";
  private static final String NO_CHANGED_FILES_LABEL = "VCS contains no changed files";
  private final ChangedFilesIssues changedFileIssues;
  private final Project project;
  private GridBagConstraints gc;
  private Timer lastAnalysisTimeUpdater;
  private JLabel lastAnalysisLabel;
  private JLabel icon;
  private JPanel panel;

  public LastAnalysisPanel(ChangedFilesIssues changedFileIssues, Project project) {
    this.changedFileIssues = changedFileIssues;
    this.project = project;
    createComponents();
    setLabel();
    setTimer();
    Disposer.register(project, this);
  }

  public JPanel getPanel() {
    return panel;
  }

  public void update() {
    setLabel();
  }

  private void setLabel() {
    LocalDateTime lastAnalysis = changedFileIssues.lastAnalysisDate();
    panel.removeAll();
    if (lastAnalysis == null) {
      ChangeListManager changeListManager = ChangeListManager.getInstance(project);
      boolean noChangedFiles = changeListManager.getAffectedFiles().isEmpty();

      if (noChangedFiles) {
        lastAnalysisLabel.setText(NO_CHANGED_FILES_LABEL);
      } else {
        lastAnalysisLabel.setText(NO_ANALYSIS_LABEL);
      }
      panel.add(icon);
      panel.add(lastAnalysisLabel, gc);
    } else {
      lastAnalysisLabel.setText("Analysis done " + SonarLintUtils.age(System.currentTimeMillis()));
      panel.add(lastAnalysisLabel, gc);
    }

    panel.add(Box.createHorizontalBox(), gc);
  }

  private void createComponents() {
    panel = new JPanel(new GridBagLayout());
    try {
      icon = new JLabel(ResourceLoader.getIcon("info.png"));
    } catch (IOException e) {
      LOGGER.error("Failed to load icon", e);
    }
    lastAnalysisLabel = new JLabel("");
    gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
  }

  @Override
  public void dispose() {
    if (lastAnalysisTimeUpdater != null) {
      lastAnalysisTimeUpdater.stop();
      lastAnalysisTimeUpdater = null;
    }
  }

  private void setTimer() {
    lastAnalysisTimeUpdater = new Timer(5000, e -> setLabel());
  }
}
