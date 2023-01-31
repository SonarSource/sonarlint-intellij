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
package org.sonarlint.intellij.ui;

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

public class LastAnalysisPanel {
  private static final String NEVER_ANALYZED_EMPTY_TEXT = "Trigger an analysis to find issues in the project sources";
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

  public LastAnalysisPanel() {
    createComponents();
    setTimer();
  }

  public JPanel getPanel() {
    return panel;
  }

  public void clear() {
    this.lastAnalysis = null;
    this.whatAnalyzed = null;
    layout.show(panel, NO_ANALYSIS);
    noAnalysisLabel.setText(NEVER_ANALYZED_EMPTY_TEXT);
    panel.repaint();
  }

  public void update(Instant lastAnalysis, String whatAnalyzed) {
    this.lastAnalysis = lastAnalysis;
    this.whatAnalyzed = whatAnalyzed;
    layout.show(panel, WITH_ANALYSIS);
    setLastAnalysisLabel();
  }

  private void setLastAnalysisLabel() {
    lastAnalysisLabel.setText("Analysis of " + whatAnalyzed + " done " + DateUtils.toAge(lastAnalysis.toEpochMilli()));
    panel.repaint();
  }

  private void createComponents() {
    layout = new CardLayout();
    panel = new JPanel(layout);
    lastAnalysisLabel = new JLabel("");
    noAnalysisLabel = new JLabel("No analysis done");

    var gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(2, 2, 2, 2), 0, 0);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;

    var noAnalysisCard = new JPanel(new GridBagLayout());
    noAnalysisCard.add(new JLabel(SonarLintIcons.INFO));
    noAnalysisCard.add(noAnalysisLabel, gc);
    noAnalysisCard.add(Box.createHorizontalBox(), gc);
    panel.add(noAnalysisCard, NO_ANALYSIS);

    var withAnalysisCard = new JPanel(new GridBagLayout());
    withAnalysisCard.add(lastAnalysisLabel, gc);
    withAnalysisCard.add(Box.createHorizontalBox(), gc);
    panel.add(withAnalysisCard, WITH_ANALYSIS);
  }

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
      }
    });
    lastAnalysisTimeUpdater.start();
  }
}
