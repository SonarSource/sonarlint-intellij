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

import com.intellij.util.ui.JBUI;
import org.sonarlint.intellij.issue.hotspot.SecurityHotspotCategory;
import org.sonarlint.intellij.issue.hotspot.LocalHotspot;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.WEST;

public class SonarLintHotspotSummaryPanel {
  private static final int MARGIN = 10;
  public static final int PADDING = 5;
  public static final double COLUMNS_PROPORTION = 0.5;
  private final JPanel panel;
  private final JLabel categoryLabel;
  private final JLabel authorLabel;
  private final JLabel statusLabel;
  private final JLabel ruleKeyLabel;

  public SonarLintHotspotSummaryPanel() {
    panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
    GridBagLayout gridLayout = new GridBagLayout();
    panel.setLayout(gridLayout);
    panel.add(new JLabel("Category"), position(0, 0));
    categoryLabel = new JLabel("");
    panel.add(categoryLabel, position(0, 1));
    panel.add(new JLabel("Author"), position(1, 0));
    authorLabel = new JLabel("");
    panel.add(authorLabel, position(1, 1));
    panel.add(new JLabel("Status"), position(2, 0));
    statusLabel = new JLabel("");
    panel.add(statusLabel, position(2, 1));
    panel.add(new JLabel("Rule key"), position(3, 0));
    ruleKeyLabel = new JLabel("");
    panel.add(ruleKeyLabel, position(3, 1));

    // Consume extra space
    panel.add(new JPanel(), new GridBagConstraints(0, 4, 2, 1, 0.0, 1.0,
      WEST, HORIZONTAL, JBUI.emptyInsets(), 0, 0));
  }

  private static GridBagConstraints position(int row, int column) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.insets = JBUI.insets(PADDING);
    constraints.anchor = GridBagConstraints.FIRST_LINE_START;
    constraints.weightx = COLUMNS_PROPORTION;
    constraints.gridx = column;
    constraints.gridy = row;
    return constraints;
  }

  public void setDetails(LocalHotspot hotspot) {
    categoryLabel.setText(getDisplayName(hotspot.getCategory()));
    authorLabel.setText(hotspot.getAuthor());
    statusLabel.setText(hotspot.getStatusDescription());
    ruleKeyLabel.setText(hotspot.getRuleKey());
  }

  private static String getDisplayName(String shortName) {
    return SecurityHotspotCategory.findByShortName(shortName)
      .map(SecurityHotspotCategory::getLongName)
      .orElse(shortName);
  }

  public JComponent getPanel() {
    return panel;
  }

}
