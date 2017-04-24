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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.ui.VerticalFlowLayout;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class SonarLintGlobalOptionsPanel {
  private JPanel rootPane;
  private JCheckBox autoTrigger;

  public SonarLintGlobalOptionsPanel(SonarLintGlobalSettings model) {
    load(model);
  }

  public JComponent getComponent() {
    if (rootPane == null) {

      rootPane = new JPanel(new BorderLayout());
      rootPane.add(createTopPanel(), BorderLayout.NORTH);
    }

    return rootPane;
  }

  private JPanel createTopPanel() {
    autoTrigger = new JCheckBox("Automatically trigger analysis");
    autoTrigger.setFocusable(false);
    JPanel tickOptions = new JPanel(new VerticalFlowLayout());
    tickOptions.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    tickOptions.add(autoTrigger);

    return tickOptions;
  }

  public boolean isModified(SonarLintGlobalSettings model) {
    getComponent();
    return model.isAutoTrigger() != autoTrigger.isSelected();
  }

  public void load(SonarLintGlobalSettings model) {
    getComponent();
    autoTrigger.setSelected(model.isAutoTrigger());
  }

  public void save(SonarLintGlobalSettings model) {
    getComponent();
    model.setAutoTrigger(autoTrigger.isSelected());
  }
}

