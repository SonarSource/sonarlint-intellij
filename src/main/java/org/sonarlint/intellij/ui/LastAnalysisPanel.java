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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import icons.SonarLintIcons;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.Instant;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.sonarsource.sonarlint.core.client.api.util.DateUtils;

public class LastAnalysisPanel implements Disposable {
  private static final String NO_ANALYSIS = "NO_ANALYSIS";
  private static final String WITH_ANALYSIS = "WITH_ANALYSIS";
  @Nullable
  private Instant lastAnalysis;
  @Nullable
  private String whatAnalyzed;
  private Timer lastAnalysisTimeUpdater;
  private JLabel lastAnalysisLabel;
  private JLabel noAnalysisLabel;
  private JPanel panel;
  private CardLayout layout;

  public LastAnalysisPanel(Project project) {
    createComponents();
    setTimer();
    Disposer.register(project, this);
  }

  public JPanel getPanel() {
    return panel;
  }

  public void update(@Nullable Instant lastAnalysis, @Nullable String whatAnalyzed, String emptyText) {
    this.lastAnalysis = lastAnalysis;
    this.whatAnalyzed = whatAnalyzed;
    setLabel(emptyText);
  }

  private void setLabel(String emptyText) {
    if (lastAnalysis == null) {
      layout.show(panel, NO_ANALYSIS);
      noAnalysisLabel.setText(emptyText);
    } else {
      layout.show(panel, WITH_ANALYSIS);
      setLastAnalysisLabel();
    }

    panel.repaint();
  }

  private void setLastAnalysisLabel() {
    lastAnalysisLabel.setText("Analysis of " + whatAnalyzed + " done " + DateUtils.toAge(lastAnalysis.toEpochMilli()));
  }

  private void createComponents() {
    layout = new CardLayout();
    panel = new JPanel(layout);
    lastAnalysisLabel = new JLabel("");
    noAnalysisLabel = new JLabel("No analysis done");

    GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(2, 2, 2, 2), 0, 0);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;

    JPanel noAnalysisCard = new JPanel(new GridBagLayout());
    noAnalysisCard.add(new JLabel(SonarLintIcons.INFO));
    noAnalysisCard.add(noAnalysisLabel, gc);
    noAnalysisCard.add(Box.createHorizontalBox(), gc);
    panel.add(noAnalysisCard, NO_ANALYSIS);

    JPanel withAnalysisCard = new JPanel(new GridBagLayout());
    withAnalysisCard.add(lastAnalysisLabel, gc);
    withAnalysisCard.add(Box.createHorizontalBox(), gc);
    panel.add(withAnalysisCard, WITH_ANALYSIS);
  }

  @Override
  public void dispose() {
    if (lastAnalysisTimeUpdater != null) {
      lastAnalysisTimeUpdater.stop();
      lastAnalysisTimeUpdater = null;
    }
  }

  private void setTimer() {
    lastAnalysisTimeUpdater = new Timer(5000, e -> {
      if (lastAnalysis != null) {
        setLastAnalysisLabel();
        panel.repaint();
      }
    });
    lastAnalysisTimeUpdater.start();
  }
}
