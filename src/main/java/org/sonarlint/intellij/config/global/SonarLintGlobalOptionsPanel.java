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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.ui.VerticalFlowLayout;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.sonarlint.intellij.config.ConfigurationPanel;

public class SonarLintGlobalOptionsPanel implements ConfigurationPanel<SonarLintGlobalSettings> {
  private JPanel rootPane;
  private JCheckBox autoTrigger;

  @Override
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

  @Override
  public boolean isModified(SonarLintGlobalSettings model) {
    getComponent();
    return model.isAutoTrigger() != autoTrigger.isSelected();
  }

  @Override
  public void load(SonarLintGlobalSettings model) {
    getComponent();
    autoTrigger.setSelected(model.isAutoTrigger());
  }

  @Override
  public void save(SonarLintGlobalSettings model) {
    getComponent();
    model.setAutoTrigger(autoTrigger.isSelected());
  }
}

