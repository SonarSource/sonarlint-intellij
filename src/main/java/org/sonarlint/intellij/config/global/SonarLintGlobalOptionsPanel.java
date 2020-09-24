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

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.sonarlint.intellij.config.ConfigurationPanel;

public class SonarLintGlobalOptionsPanel implements ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String NODE_JS_TOOLTIP = "SonarLint requires Node.js to analyze some languages. You can provide an explicit path for the node executable here or leave this field blank to let SonarLint look for it using your PATH environment variable.";
  private JPanel rootPane;
  private JCheckBox autoTrigger;
  private TextFieldWithBrowseButton nodeJsPath;

  @Override
  public JComponent getComponent() {
    if (rootPane == null) {
      rootPane = new JPanel(new BorderLayout());
      rootPane.add(createTopPanel(), BorderLayout.NORTH);
    }

    return rootPane;
  }

  private JPanel createTopPanel() {
    JPanel optionsPanel = new JPanel(new GridBagLayout());
    optionsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;

    autoTrigger = new JCheckBox("Automatically trigger analysis");
    autoTrigger.setFocusable(false);
    c.gridx = 0;
    c.gridy = 0;
    c.gridwidth = 2;
    c.anchor = GridBagConstraints.WEST;
    optionsPanel.add(autoTrigger, c);

    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 1;
    JLabel label = new JLabel("Node.js path: ");
    label.setToolTipText(NODE_JS_TOOLTIP);
    optionsPanel.add(label, c);
    nodeJsPath = new TextFieldWithBrowseButton();
    nodeJsPath.setToolTipText(NODE_JS_TOOLTIP);
    FileChooserDescriptor fileChooser = new FileChooserDescriptor(true, false, false, false, false, false);
    nodeJsPath.addBrowseFolderListener("Select Node.js Binary", "Select Node.js binary to be used by SonarLint", null, fileChooser);
    c.gridx = 1;
    c.gridy = 1;
    c.weightx = 1.0;
    optionsPanel.add(nodeJsPath, c);

    return optionsPanel;
  }

  @Override
  public boolean isModified(SonarLintGlobalSettings model) {
    getComponent();
    return model.isAutoTrigger() != autoTrigger.isSelected() || !Objects.equals(model.getNodejsPath(), nodeJsPath.getText());
  }

  @Override
  public void load(SonarLintGlobalSettings model) {
    getComponent();
    autoTrigger.setSelected(model.isAutoTrigger());
    nodeJsPath.setText(model.getNodejsPath());
  }

  @Override
  public void save(SonarLintGlobalSettings model) {
    getComponent();
    model.setAutoTrigger(autoTrigger.isSelected());
    model.setNodejsPath(nodeJsPath.getText());
  }
}

