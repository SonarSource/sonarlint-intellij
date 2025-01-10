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

import static org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.RULE_SECTION_LINK;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.RULE_SELECTION_PAGE;

public class WelcomePage {
  private final JPanel welcomePagePanel;

  public WelcomePage(Project project, JButton welcomePageNextButton) {
    var font = UIUtil.getLabelFont();
    welcomePagePanel = new JPanel(new BorderLayout());

    var icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/sonarqube-for-ide-mark.png")));
    var welcomeImageLabel = new JLabel(icon);

    var welcomeStepLabel = new JLabel("Step 1/4", SwingConstants.LEFT);
    welcomeStepLabel.setFont(new Font(SonarLintWalkthroughToolWindow.FONT, Font.PLAIN, 14));

    var titleLabel = new JLabel("Get started", SwingConstants.LEFT);
    titleLabel.setFont(new Font(SonarLintWalkthroughToolWindow.FONT, Font.BOLD, 16));
    var welcomePageText = createWelcomePageText(font, project);

    var welcomePageScrollPane = new JScrollPane(welcomePageText);
    welcomePageScrollPane.setBorder(null);
    welcomePageScrollPane.setPreferredSize(new Dimension(70, 100));

    var welcomePageNextButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    welcomePageNextButtonPanel.add(welcomePageNextButton);

    createWelcomePageLayout(welcomeStepLabel, titleLabel, welcomePageScrollPane, welcomePageNextButtonPanel, welcomePagePanel,
      welcomeImageLabel);
  }

  public JPanel getPanel() {
    return welcomePagePanel;
  }

  private static void createWelcomePageLayout(JLabel stepLabel, JLabel titleLabel, JScrollPane welcomePageScrollPane,
    JPanel welcomePageNextButtonPanel, JPanel welcomePagePanel, JLabel welcomePageImageLabel) {
    var gbc = new GridBagConstraints();

    var centerPanel = SonarLintWalkthroughUtils.createCenterPanel(stepLabel, titleLabel, welcomePageScrollPane, gbc);

    gbc.anchor = GridBagConstraints.SOUTHEAST;
    SonarLintWalkthroughUtils.provideCommonButtonConstraints(gbc);
    centerPanel.add(welcomePageNextButtonPanel, gbc);

    welcomePagePanel.add(welcomePageImageLabel, BorderLayout.NORTH);
    welcomePagePanel.add(centerPanel, BorderLayout.CENTER);
  }

  private static JEditorPane createWelcomePageText(Font font, Project project) {
    var descriptionPane = new JEditorPane(SonarLintWalkthroughToolWindow.EDITOR_PANE_TYPE,
      "<html><body style=\"font-family: " + font.getFamily() +
        "; font-size: " + font.getSize() + "pt; padding: 5px;\">" +
        SonarLintWalkthroughToolWindow.SONARQUBE_FOR_IDE + " supports the analysis of 15+ languages including Python, Java, Javascript, " +
        "IaC" +
        " domains along with secrets " +
        "detection. " +
        "<a href=\"" + RULE_SECTION_LINK + "\">Learn more</a>.<br><br>" +
        "Detect issues while you code in an open files or run the analysis on more file in the <a href=\"#reportView\">report view</a>" +
        ".<br><br>" +
        "Open a file and start your clean code journey.</body></html>");

    descriptionPane.setEditable(false);
    descriptionPane.setOpaque(false);

    descriptionPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if ("#reportView".equals(e.getDescription())) {
          var sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow(SonarLintWalkthroughToolWindow.SONARQUBE_FOR_IDE);
          if (sonarqubeToolWindow != null) {
            if (!sonarqubeToolWindow.isVisible()) {
              sonarqubeToolWindow.activate(null);
            }
            var reportContent = sonarqubeToolWindow.getContentManager().findContent("Report");
            if (reportContent != null) {
              sonarqubeToolWindow.getContentManager().setSelectedContent(reportContent);
            }
          }
        } else {
          RULE_SELECTION_PAGE.browseWithTelemetry();
        }
      }
    });
    return descriptionPane;
  }
}
