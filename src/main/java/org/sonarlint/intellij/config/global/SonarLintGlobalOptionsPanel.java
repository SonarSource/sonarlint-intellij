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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.sonarlint.intellij.cayc.CleanAsYouCodeService;
import org.sonarlint.intellij.cayc.NewCodeModeHelpLabel;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.core.NodeJsManager;

import static java.awt.GridBagConstraints.WEST;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class SonarLintGlobalOptionsPanel implements ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String NODE_JS_TOOLTIP = "SonarLint requires Node.js to analyze some languages. You can provide an explicit path for the node executable here or leave " +
    "this field blank to let SonarLint look for it using your PATH environment variable.";
  private JPanel rootPane;
  private JBCheckBox focusOnNewCode;
  private JBIntSpinner fieldInput;
  private JBCheckBox autoTrigger;
  private JBTextField nodeJsPath;
  private JBLabel nodeJsVersion;

  @Override
  public JComponent getComponent() {
    if (rootPane == null) {
      rootPane = new JBPanel<>(new BorderLayout());
      rootPane.add(createTopPanel(), BorderLayout.NORTH);
    }

    return rootPane;
  }

  private JPanel createTopPanel() {
    var optionsPanel = new JPanel(new GridBagLayout());
    optionsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

    focusOnNewCode = new JBCheckBox("Focus on new code");
    focusOnNewCode.setFocusable(false);
    var helpFocusLabel = NewCodeModeHelpLabel.createFocus();
    var horizontalLayoutFocus = new JPanel(new HorizontalLayout(5));
    horizontalLayoutFocus.add(focusOnNewCode);
    horizontalLayoutFocus.add(helpFocusLabel);
    optionsPanel.add(horizontalLayoutFocus, new GridBagConstraints(0, 0, 3, 1, 0.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    var horizontalLayoutDefinition = new JPanel(new HorizontalLayout(5));
    var newCodeDefinition = getService(CleanAsYouCodeService.class).getNewCodeDefinitionDays();
    var helpDefinitionLabel = NewCodeModeHelpLabel.createDefinition();
    var definitionLabel = new JLabel("New code definition is from last");
    fieldInput = new JBIntSpinner(newCodeDefinition, 1, 90);
    fieldInput.setToolTipText("Value goes from 1 to 90 days");
    var daysLabel = new JLabel("days");
    horizontalLayoutDefinition.add(definitionLabel);
    horizontalLayoutDefinition.add(fieldInput);
    horizontalLayoutDefinition.add(daysLabel);
    horizontalLayoutDefinition.add(helpDefinitionLabel);
    optionsPanel.add(horizontalLayoutDefinition, new GridBagConstraints(0, 1, 3, 1, 0.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.insets(0, 24, 0, 0), 0, 0));
    autoTrigger = new JBCheckBox("Automatically trigger analysis");
    autoTrigger.setFocusable(false);
    optionsPanel.add(autoTrigger, new GridBagConstraints(0, 2, 3, 1, 0.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    var label = new JLabel("Node.js path: ");
    label.setToolTipText(NODE_JS_TOOLTIP);
    optionsPanel.add(label, new GridBagConstraints(0, 3, 1, 1, 0.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    nodeJsPath = new JBTextField();
    var nodeJsPathWithBrowse = new TextFieldWithBrowseButton(nodeJsPath);
    nodeJsPathWithBrowse.setToolTipText(NODE_JS_TOOLTIP);
    var fileChooser = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    nodeJsPathWithBrowse.addBrowseFolderListener("Select Node.js Binary", "Select Node.js binary to be used by SonarLint", null, fileChooser);
    optionsPanel.add(nodeJsPathWithBrowse, new GridBagConstraints(1, 3, 1, 1, 1.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    nodeJsVersion = new JBLabel();
    optionsPanel.add(nodeJsVersion, new GridBagConstraints(2, 3, 1, 1, 0.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    return optionsPanel;
  }

  @Override
  public boolean isModified(SonarLintGlobalSettings model) {
    getComponent();
    return model.isFocusOnNewCode() != focusOnNewCode.isSelected()
      || model.isAutoTrigger() != autoTrigger.isSelected()
      || !Objects.equals(model.getNodejsPath(), nodeJsPath.getText())
      || model.getNewCodeDefinitionDays() != fieldInput.getNumber();
  }

  @Override
  public void load(SonarLintGlobalSettings model) {
    getComponent();
    focusOnNewCode.setSelected(model.isFocusOnNewCode());
    fieldInput.setNumber(model.getNewCodeDefinitionDays());
    autoTrigger.setSelected(model.isAutoTrigger());
    nodeJsPath.setText(model.getNodejsPath());
    final var nodeJsManager = getService(NodeJsManager.class);
    final var detectedNodeJsPath = nodeJsManager.getNodeJsPath();
    this.nodeJsPath.getEmptyText().setText(detectedNodeJsPath != null ? detectedNodeJsPath.toString() : "Node.js not found");
    final var detectedNodeJsVersion = nodeJsManager.getNodeJsVersion();
    this.nodeJsVersion.setText(detectedNodeJsVersion != null ? detectedNodeJsVersion.toString() : "N/A");
  }

  @Override
  public void save(SonarLintGlobalSettings settings) {
    getComponent();
    getService(CleanAsYouCodeService.class).setFocusOnNewCode(focusOnNewCode.isSelected(), settings);
    getService(CleanAsYouCodeService.class).setNewCodeDefinition(fieldInput.getNumber(), settings);
    settings.setAutoTrigger(autoTrigger.isSelected());
    settings.setNodejsPath(nodeJsPath.getText());
  }
}

