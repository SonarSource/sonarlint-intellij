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

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.util.Objects;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;

import static org.sonarlint.intellij.ui.SonarLintWalkthroughToolWindow.EDITOR_PANE_TYPE;
import static org.sonarlint.intellij.ui.SonarLintWalkthroughToolWindow.SONARQUBE_FOR_IDE;

public class LearnAsYouCodePage {
  private final JPanel learnAsYouCodePagePanel;

  public LearnAsYouCodePage(Project project, JButton learnAsYouCodePageNextButton, JButton learnAsYouCodePageBackButton) {
    var font = UIUtil.getLabelFont();
    learnAsYouCodePagePanel = new JPanel(new BorderLayout());

    var icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/sonarqube-for-ide-mark.png")));
    var learnAsYouCodeImageLabel = new JLabel(icon);

    var learnAsYouCodeStepLabel = new JLabel("Step 2/4", SwingConstants.LEFT);
    learnAsYouCodeStepLabel.setFont(new Font(SonarLintWalkthroughToolWindow.FONT, Font.PLAIN, 14));

    var learnAsYouCodePageLabel = new JLabel("Learn as you code");
    learnAsYouCodePageLabel.setFont(new Font(SonarLintWalkthroughToolWindow.FONT, Font.BOLD, 16));
    var learnAsYouCodeText = createLearnAsYouCodePageText(font, project);

    var learnAsYouCodeScrollPane = new JScrollPane(learnAsYouCodeText);
    learnAsYouCodeScrollPane.setBorder(null);
    learnAsYouCodeScrollPane.setPreferredSize(new Dimension(SonarLintWalkthroughToolWindow.WIDTH, SonarLintWalkthroughToolWindow.HEIGHT));

    var learnAsYouCodePageBackButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    learnAsYouCodePageBackButtonPanel.add(learnAsYouCodePageBackButton);

    var learnAsYouCodePageNextButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    learnAsYouCodePageNextButtonPanel.add(learnAsYouCodePageNextButton);

    createLearnAsYouCodePageLayout(learnAsYouCodeStepLabel, learnAsYouCodePageLabel, learnAsYouCodeScrollPane,
      learnAsYouCodePageBackButtonPanel, learnAsYouCodePageNextButtonPanel, learnAsYouCodePagePanel, learnAsYouCodeImageLabel);
  }

  public JPanel getPanel() {
    return learnAsYouCodePagePanel;
  }

  private static void createLearnAsYouCodePageLayout(JLabel stepLabel, JLabel label, JScrollPane pane,
    JPanel backButtonPanel, JPanel nextButtonPanel,
    JPanel page, JLabel imageLabel) {
    var gbc = new GridBagConstraints();

    var centerPanel = SonarLintWalkthroughUtils.createCenterPanel(stepLabel, label, pane, gbc);

    gbc.anchor = GridBagConstraints.SOUTHWEST;
    SonarLintWalkthroughUtils.provideCommonButtonConstraints(gbc);
    centerPanel.add(backButtonPanel, gbc);

    gbc.anchor = GridBagConstraints.SOUTHEAST;
    centerPanel.add(nextButtonPanel, gbc);

    page.add(imageLabel, BorderLayout.NORTH);
    page.add(centerPanel, BorderLayout.CENTER);
  }

  private static JEditorPane createLearnAsYouCodePageText(Font font, Project project) {
    var descriptionPane = new JEditorPane(EDITOR_PANE_TYPE, "<html><body style=\"font-family: " + font.getFamily() +
      "; font-size: " + font.getSize() + "pt; padding: 5px;\">" +
      "Check the <a href=\"#currentFile\">Current File</a> view: When " + SONARQUBE_FOR_IDE + " found something, click on the issue to " +
      "get the rule " +
      "description and an example of compliant code.<br><br>" +
      "Some rules offer quick fixes when you hover over the issue location.<br><br>" +
      "Finally you can disable rules in the <a href=\"#settings\">settings</a>.</body></html>");

    descriptionPane.setEditable(false);
    descriptionPane.setOpaque(false);

    descriptionPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if ("#currentFile".equals(e.getDescription())) {
          var sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow(SONARQUBE_FOR_IDE);
          if (sonarqubeToolWindow != null) {
            if (!sonarqubeToolWindow.isVisible()) {
              sonarqubeToolWindow.activate(null);
            }
            var currentFileContent = sonarqubeToolWindow.getContentManager().findContent("Current File");
            if (currentFileContent != null) {
              sonarqubeToolWindow.getContentManager().setSelectedContent(currentFileContent);
            }
          }
        } else if ("#settings".equals(e.getDescription())) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, SonarLintGlobalConfigurable.class);
        }
      }
    });
    return descriptionPane;
  }
}
