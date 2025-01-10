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
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable;

import static org.sonarlint.intellij.telemetry.LinkTelemetry.AI_FIX_SUGGESTIONS_PAGE;
import static org.sonarlint.intellij.ui.SonarLintWalkthroughToolWindow.SONARQUBE_FOR_IDE;

public class ConnectWithYourTeamPage {
  private final JPanel connectWithYourTeamPagePanel;

  public ConnectWithYourTeamPage(Project project, JButton connectWithYourTeamNextButton, JButton connectWithYourTeamBackButton) {
    var font = UIUtil.getLabelFont();
    connectWithYourTeamPagePanel = new JPanel(new BorderLayout());

    var icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/sonarqube-for-ide-mark.png")));
    var connectWithYourTeamImageLabel = new JLabel(icon);

    var connectWithYourTeamStepLabel = new JLabel("Step 3/4", SwingConstants.LEFT);
    connectWithYourTeamStepLabel.setFont(new Font(SonarLintWalkthroughToolWindow.FONT, Font.PLAIN, 14));

    var connectWithYourTeamLabel = new JLabel("Connect with your team");
    connectWithYourTeamLabel.setFont(new Font(SonarLintWalkthroughToolWindow.FONT, Font.BOLD, 16));
    var connectWithYourTeamText = createConnectWithYourTeamPageText(font, project);

    var connectWithYourTeamScrollPane = new JScrollPane(connectWithYourTeamText);
    connectWithYourTeamScrollPane.setBorder(null);
    connectWithYourTeamScrollPane.setPreferredSize(new Dimension(SonarLintWalkthroughToolWindow.WIDTH,
      SonarLintWalkthroughToolWindow.HEIGHT));

    var connectWithYourTeamBackButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    connectWithYourTeamBackButtonPanel.add(connectWithYourTeamBackButton);

    var connectWithYourTeamNextButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    connectWithYourTeamNextButtonPanel.add(connectWithYourTeamNextButton);

    createConnectWithYourTeamLayout(connectWithYourTeamStepLabel, connectWithYourTeamLabel, connectWithYourTeamScrollPane,
      connectWithYourTeamBackButtonPanel, connectWithYourTeamNextButtonPanel, connectWithYourTeamPagePanel, connectWithYourTeamImageLabel);
  }

  public JPanel getPanel() {
    return connectWithYourTeamPagePanel;
  }

  private static void createConnectWithYourTeamLayout(JLabel stepLabel, JLabel label, JScrollPane pane,
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

  private static JEditorPane createConnectWithYourTeamPageText(Font font, Project project) {
    var descriptionPane = new JEditorPane(SonarLintWalkthroughToolWindow.EDITOR_PANE_TYPE,
      "<html><body style=\"font-family: " + font.getFamily() +
        "; font-size: " + font.getSize() + "pt; padding: 5px;\">" +
        "Apply the same set of rules as your team by using " + SONARQUBE_FOR_IDE + " in Connected Mode with SonarQube Cloud or SonarQube " +
        "Server" +
        ".<br><br>" +
        "With connected mode, benefit from advanced analysis like <a href=\"#taintVulnerabilities\">Taint Vulnerabilities</a> and open " +
        "issues and <a href=\"#aiFixSuggestions\">AI fix suggestions</a> from SonarQube Server or Cloud in the IDE.<br><br>" +
        "Already using SonarQube Cloud or Server? <a href=\"#setupConnection\">Set up a connection</a>.</body></html>");

    descriptionPane.setEditable(false);
    descriptionPane.setOpaque(false);

    descriptionPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if ("#setupConnection".equals(e.getDescription())) {
          var configurable = new SonarLintProjectConfigurable(project);
          ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
        } else if ("#taintVulnerabilities".equals(e.getDescription())) {
          var sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow(SONARQUBE_FOR_IDE);
          if (sonarqubeToolWindow != null) {
            if (!sonarqubeToolWindow.isVisible()) {
              sonarqubeToolWindow.activate(null);
            }
            var taintVulnerabilitiesContent = sonarqubeToolWindow.getContentManager().findContent("Taint Vulnerabilities");
            if (taintVulnerabilitiesContent != null) {
              sonarqubeToolWindow.getContentManager().setSelectedContent(taintVulnerabilitiesContent);
            }
          }
        } else {
          AI_FIX_SUGGESTIONS_PAGE.browseWithTelemetry();
        }
      }
    });
    return descriptionPane;
  }
}
