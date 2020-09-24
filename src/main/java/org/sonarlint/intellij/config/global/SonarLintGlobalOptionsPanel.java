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
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.nio.file.Path;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.core.NodeJsManager;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.Version;

public class SonarLintGlobalOptionsPanel implements ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String NODE_JS_TOOLTIP = "SonarLint requires Node.js to analyze some languages. You can provide an explicit path for the node executable here or leave this field blank to let SonarLint look for it using your PATH environment variable.";
  private JPanel rootPane;
  private JCheckBox autoTrigger;
  private JBTextField nodeJsPath;
  private JLabel nodeJsVersion;

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
    c.gridwidth = 3;
    c.anchor = GridBagConstraints.WEST;
    optionsPanel.add(autoTrigger, c);

    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 1;
    JLabel label = new JLabel("Node.js path: ");
    label.setToolTipText(NODE_JS_TOOLTIP);
    optionsPanel.add(label, c);
    nodeJsPath = new JBTextField();
    TextFieldWithBrowseButton nodeJsPathWithBrowse = new TextFieldWithBrowseButton(nodeJsPath);
    nodeJsPathWithBrowse.setToolTipText(NODE_JS_TOOLTIP);
    FileChooserDescriptor fileChooser = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    nodeJsPathWithBrowse.addBrowseFolderListener("Select Node.js Binary", "Select Node.js binary to be used by SonarLint", null, fileChooser);
    c.gridx = 1;
    c.gridy = 1;
    c.weightx = 1.0;
    optionsPanel.add(nodeJsPathWithBrowse, c);
    nodeJsVersion = new JLabel();
    c.gridx = 2;
    c.gridy = 1;
    c.weightx = 0.0;
    optionsPanel.add(nodeJsVersion, c);
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
    final NodeJsManager nodeJsManager = SonarLintUtils.getService(NodeJsManager.class);
    final Path detectedNodeJsPath = nodeJsManager.getNodeJsPath();
    this.nodeJsPath.getEmptyText().setText(detectedNodeJsPath != null ? detectedNodeJsPath.toString() : "Node.js not found");
    final Version detectedNodeJsVersion = nodeJsManager.getNodeJsVersion();
    this.nodeJsVersion.setText(detectedNodeJsVersion != null ? detectedNodeJsVersion.toString() : "N/A");
  }

  @Override
  public void save(SonarLintGlobalSettings model) {
    getComponent();
    model.setAutoTrigger(autoTrigger.isSelected());
    model.setNodejsPath(nodeJsPath.getText());
  }
}

