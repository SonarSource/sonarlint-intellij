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

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import icons.SonarLintIcons;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;

class HotspotCellRenderer implements ListCellRenderer {

  private static final int WHITE = 0xffffff;
  private final JBColor whiteForeground = new JBColor(WHITE, WHITE);

  private static final String ICON_KEY = "security_hotspot_";

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    var hotspot = (LiveSecurityHotspot) value;
    var layout = new FlowLayout(FlowLayout.LEFT);
    var panel = new JPanel(layout);

    var shIconLabel = new JBLabel();
    var icon = SonarLintIcons.type(ICON_KEY + hotspot.getVulnerabilityProbability().name());
    shIconLabel.setIcon(icon);
    shIconLabel.setToolTipText("Security Hotspots with severity " + hotspot.getVulnerabilityProbability().name());
    panel.add(shIconLabel, HorizontalLayout.LEFT);

    var analyzedByLabel = new JBLabel();
    if (hotspot.getServerFindingKey() != null) {
      analyzedByLabel.setIcon(SonarLintIcons.ICON_SONARQUBE_16);
      analyzedByLabel.setToolTipText("Analyzed by SonarQube");
    } else {
      analyzedByLabel.setToolTipText("Analyzed by SonarLint");
      analyzedByLabel.setIcon(SonarLintIcons.SONARLINT);
    }
    panel.add(analyzedByLabel, HorizontalLayout.LEFT);


    var messageLabel = new JLabel(hotspot.getMessage());
    messageLabel.setForeground(isSelected ? whiteForeground : JBColor.BLACK);
    panel.add(messageLabel);

    var file = hotspot.getFile();
    var fileName = file == null ? hotspot.getFile().getPath() : file.getName();
    var lineNumber = hotspot.getLine();
    var lineLabel = new JLabel(fileName + (lineNumber != null ? (":" + lineNumber) : ""));
    lineLabel.setForeground(isSelected ? whiteForeground : JBColor.GRAY);
    panel.add(lineLabel);

    return panel;
  }
}
