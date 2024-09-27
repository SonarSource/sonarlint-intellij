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
package org.sonarlint.intellij.config.global;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.nio.file.Paths;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.cayc.CleanAsYouCodeService;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.util.HelpLabelUtils;

import static java.awt.GridBagConstraints.WEST;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.FOCUS_ON_NEW_CODE_LINK;

public class SonarLintGlobalOptionsPanel implements ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String NODE_JS_TOOLTIP = "SonarLint requires Node.js to analyze some languages. You can provide an explicit path for the node executable here or leave " +
    "this field blank to let SonarLint look for it using your PATH environment variable." +
    " Restarting your IDE is recommended.";
  private JPanel rootPane;
  private JBCheckBox autoTrigger;
  private JBTextField nodeJsPath;
  private JBLabel nodeJsVersion;
  private JBCheckBox focusOnNewCode;

  @Override
  public JComponent getComponent() {
    if (rootPane == null) {
      rootPane = new JBPanel<>(new BorderLayout());
      focusOnNewCode = new JBCheckBox("Focus on new code");
      focusOnNewCode.setFocusable(false);
      var helpLabel = HelpLabelUtils.createCleanAsYouCode();
      var cleanAsYouCodeOption = new JPanel(new HorizontalLayout(5));
      cleanAsYouCodeOption.add(focusOnNewCode);
      cleanAsYouCodeOption.add(helpLabel);
      var cleanAsYouCodeDocumentation = new JPanel(new HorizontalLayout(5));
      cleanAsYouCodeDocumentation.add(cleanAsYouCodeLink());
      rootPane.add(createTopPanel(), BorderLayout.SOUTH);
      rootPane.add(cleanAsYouCodeDocumentation, BorderLayout.CENTER);
      rootPane.add(cleanAsYouCodeOption, BorderLayout.NORTH);
    }

    return rootPane;
  }

  private JEditorPane cleanAsYouCodeLink() {
    var connectedModeLabel = new JEditorPane();
    initHtmlPane(connectedModeLabel);
    connectedModeLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 15, 0));
    SwingHelper.setHtml(connectedModeLabel, "Focusing on new code helps you practice"+
      " <a href=\"" + FOCUS_ON_NEW_CODE_LINK + "\">Clean as You Code.</a>",
      JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    connectedModeLabel .addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        BrowserUtil.browse(FOCUS_ON_NEW_CODE_LINK);
      }
    });
    return connectedModeLabel;
  }

  private JPanel createTopPanel() {
    var optionsPanel = new JPanel(new GridBagLayout());
    optionsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    autoTrigger = new JBCheckBox("Automatically trigger analysis");
    autoTrigger.setFocusable(false);
    optionsPanel.add(autoTrigger, new GridBagConstraints(0, 1, 3, 1, 0.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    var label = new JLabel("Node.js path: ");
    label.setToolTipText(NODE_JS_TOOLTIP);
    optionsPanel.add(label, new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    nodeJsPath = new JBTextField();
    var nodeJsPathWithBrowse = new TextFieldWithBrowseButton(nodeJsPath);
    nodeJsPathWithBrowse.setToolTipText(NODE_JS_TOOLTIP);
    var fileChooser = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    nodeJsPathWithBrowse.addBrowseFolderListener("Select Node.js Binary", "Select Node.js binary to be used by SonarLint", null, fileChooser);
    optionsPanel.add(nodeJsPathWithBrowse, new GridBagConstraints(1, 2, 1, 1, 1.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    nodeJsVersion = new JBLabel();
    optionsPanel.add(nodeJsVersion, new GridBagConstraints(2, 2, 1, 1, 0.0, 0.0,
      WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

    return optionsPanel;
  }

  @Override
  public boolean isModified(SonarLintGlobalSettings model) {
    getComponent();
    return model.isAutoTrigger() != autoTrigger.isSelected()
      || !Objects.equals(model.getNodejsPath(), nodeJsPath.getText())
      || model.isFocusOnNewCode() != focusOnNewCode.isSelected();
  }

  @Override
  public void load(SonarLintGlobalSettings model) {
    getComponent();
    autoTrigger.setSelected(model.isAutoTrigger());
    nodeJsPath.setText(model.getNodejsPath());
    focusOnNewCode.setSelected(model.isFocusOnNewCode());
    loadNodeJsSettings(model);
  }

  private void loadNodeJsSettings(SonarLintGlobalSettings model) {
    if (model.getNodejsPath() == null || model.getNodejsPath().isBlank()) {
      getService(BackendService.class).getAutoDetectedNodeJs().thenAccept(settings -> {
        if (settings == null) {
          this.nodeJsPath.getEmptyText().setText("Node.js not found");
          this.nodeJsVersion.setText("N/A");
        } else {
          this.nodeJsPath.getEmptyText().setText(settings.getPath().toString());
          this.nodeJsVersion.setText(settings.getVersion());
        }
      });
    } else {
      var forcedNodeJsPath = Paths.get(model.getNodejsPath());
      getService(BackendService.class).changeClientNodeJsPath(forcedNodeJsPath).thenAccept(settings -> {
        if (settings == null) {
          this.nodeJsVersion.setText("N/A");
        } else {
          this.nodeJsPath.setText(settings.getPath().toString());
          this.nodeJsVersion.setText(settings.getVersion());
        }
      });
    }
  }

  @Override
  public void save(SonarLintGlobalSettings settings) {
    getComponent();
    getService(CleanAsYouCodeService.class).setFocusOnNewCode(focusOnNewCode.isSelected(), settings);
    settings.setAutoTrigger(autoTrigger.isSelected());
    settings.setNodejsPath(nodeJsPath.getText());
  }
}

