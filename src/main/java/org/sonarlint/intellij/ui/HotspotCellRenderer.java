/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.tree.TreeCellRenderer;
import org.sonarlint.intellij.ui.nodes.HotspotNode;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;

class HotspotCellRenderer implements TreeCellRenderer {

  private static final int HORIZONTAL_PADDING = 15;

  private static final Map<VulnerabilityProbability, JBColor> colorsByProbability = new EnumMap<>(VulnerabilityProbability.class);

  private static final int RED = 0xd4333f;
  private static final int ORANGE = 0xed7d20;
  private static final int YELLOW = 0xeabe06;
  private static final int WHITE = 0xffffff;

  private final JBColor whiteForeground = new JBColor(WHITE, WHITE);

  static {
    colorsByProbability.put(VulnerabilityProbability.HIGH, new JBColor(RED, RED));
    colorsByProbability.put(VulnerabilityProbability.MEDIUM, new JBColor(ORANGE, ORANGE));
    colorsByProbability.put(VulnerabilityProbability.LOW, new JBColor(YELLOW, YELLOW));
  }

  @Override
  public Component getTreeCellRendererComponent(JTree jTree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    var hotspot = ((HotspotNode) value).getHotspot();
    var layout = new FlowLayout(FlowLayout.LEADING);
    var panel = new JPanel(layout);
    var primaryLocation = hotspot.getPrimaryLocation();

    var probabilityLabel = new JLabel(hotspot.getProbability().name());
    var border = BorderFactory.createEmptyBorder(0, HORIZONTAL_PADDING, 0, HORIZONTAL_PADDING);
    probabilityLabel.setBorder(border);
    probabilityLabel.setVerticalTextPosition(SwingConstants.TOP);
    probabilityLabel.setBackground(primaryLocation.exists() ? colorsByProbability.get(hotspot.getProbability()) : JBColor.GRAY);
    probabilityLabel.setForeground(whiteForeground);
    probabilityLabel.setOpaque(true);
    probabilityLabel.setFont(probabilityLabel.getFont().deriveFont(probabilityLabel.getFont().getStyle() | Font.BOLD));
    panel.add(probabilityLabel);

    var messageLabel = new JLabel(hotspot.getMessage());
    messageLabel.setForeground(selected ? whiteForeground : JBColor.BLACK);
    panel.add(messageLabel);

    var file = hotspot.getPrimaryLocation().getFile();
    var fileName = file == null ? hotspot.getFilePath() : file.getName();
    var lineNumber = hotspot.getLineNumber();
    var lineLabel = new JLabel(fileName + (lineNumber != null ? (":" + lineNumber) : ""));
    lineLabel.setForeground(selected ? whiteForeground : JBColor.GRAY);
    panel.add(lineLabel);
    return panel;
  }
}
