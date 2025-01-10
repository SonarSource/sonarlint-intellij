/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;

public class SonarLintWalkthroughUtils {

  public static @NotNull JPanel createCenterPanel(JLabel stepLabel, JLabel pageLabel, JScrollPane scrollPane, GridBagConstraints gbc) {
    var centerPanel = new JPanel(new GridBagLayout());
    pageLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 0));
    stepLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 0));

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    centerPanel.add(stepLabel, gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    centerPanel.add(pageLabel, gbc);

    gbc.gridy = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    centerPanel.add(scrollPane, gbc);

    return centerPanel;
  }

  public static void provideCommonButtonConstraints(GridBagConstraints gbc) {
    gbc.gridy = 3;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    gbc.weighty = 0;
  }
}
