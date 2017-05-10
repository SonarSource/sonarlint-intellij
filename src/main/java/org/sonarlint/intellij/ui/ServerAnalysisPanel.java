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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import icons.SonarLintIcons;
import org.sonarlint.intellij.issue.ServerIssues;
import org.sonarlint.intellij.util.SonarLintUtils;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;

public class ServerAnalysisPanel implements Disposable {
  private static final String NO_SERVER_FILES_LABEL = "Server issues are empty";
  private final ServerIssues serverIssues;
  private final Project project;
  private GridBagConstraints gc;
  private JLabel lastAnalysisLabel;
  private JLabel icon;
  private JPanel panel;

  public ServerAnalysisPanel(ServerIssues serverIssues, Project project) {
    this.serverIssues = serverIssues;
    this.project = project;
    createComponents();
    setLabel();
    Disposer.register(project, this);
  }

  public JPanel getPanel() {
    return panel;
  }

  public void update() {
    setLabel();
  }

  private void setLabel() {
    LocalDateTime lastAnalysis = serverIssues.lastAnalysisDate();
    panel.removeAll();
    if (lastAnalysis == null) {
      lastAnalysisLabel.setText(NO_SERVER_FILES_LABEL);
      panel.add(icon);
      panel.add(lastAnalysisLabel, gc);
    } else {
      if (serverIssues.lastAnalysisDate() != null) {
        lastAnalysisLabel.setText("Analysis from server time " + SonarLintUtils.printDateTime(serverIssues.lastAnalysisDate()));
      } else {
        lastAnalysisLabel.setText("Analysis from unknown server time");
      }
      panel.add(lastAnalysisLabel, gc);
    }

    panel.add(Box.createHorizontalBox(), gc);
  }

  private void createComponents() {
    panel = new JPanel(new GridBagLayout());
    icon = new JLabel(SonarLintIcons.INFO);
    lastAnalysisLabel = new JLabel("");
    gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
  }

  @Override
  public void dispose() {
  }
}
