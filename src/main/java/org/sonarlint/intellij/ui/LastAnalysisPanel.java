/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.Instant;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.Timer;
import org.sonarlint.intellij.util.DateUtils;

public class LastAnalysisPanel extends JBPanel<LastAnalysisPanel> {

  protected static final String NEVER_ANALYZED_EMPTY_TEXT = "Trigger an analysis to find findings in the project sources";
  @Nullable
  private Instant lastAnalysis;
  @Nullable
  private String whatAnalyzed;
  private Timer lastAnalysisTimeUpdater;
  private JBLabel lastAnalysisLabel;

  public LastAnalysisPanel() {
    super(new GridBagLayout());
    createComponents();
    setTimer();
  }

  public void clear() {
    this.lastAnalysis = null;
    this.whatAnalyzed = null;
  }

  public void update(Instant lastAnalysis, String whatAnalyzed) {
    this.lastAnalysis = lastAnalysis;
    this.whatAnalyzed = whatAnalyzed;
    setLastAnalysisLabel();
  }

  private void setLastAnalysisLabel() {
    lastAnalysisLabel.setText("Analysis of " + whatAnalyzed + " done " + DateUtils.toAge(lastAnalysis.toEpochMilli()));
    repaint();
  }

  private void createComponents() {
    lastAnalysisLabel = new JBLabel("");

    var gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(2), 0, 0);
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;

    add(lastAnalysisLabel, gc);
    add(Box.createHorizontalBox(), gc);
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
