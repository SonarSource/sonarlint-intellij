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

import com.intellij.ide.BrowserUtil;
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

import static org.sonarlint.intellij.telemetry.LinkTelemetry.BASE_DOCS_PAGE;

public class ReachOutToUsPage {
  private final JPanel reachOutToUsPagePanel;

  public ReachOutToUsPage(Project project, JButton reachOutToUsBackButton, JButton closeButton) {
    var font = UIUtil.getLabelFont();
    reachOutToUsPagePanel = new JPanel(new BorderLayout());

    var icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/sonarqube-for-ide-mark.png")));
    var reachOutToUsImageLabel = new JLabel(icon);

    var reachOutToUsStepLabel = new JLabel("Step 4/4", SwingConstants.LEFT);
    reachOutToUsStepLabel.setFont(new Font(SonarLintWalkthroughToolWindow.FONT, Font.PLAIN, 14));

    var reachOutToUsLabel = new JLabel("Reach out to us");
    reachOutToUsLabel.setFont(new Font(SonarLintWalkthroughToolWindow.FONT, Font.BOLD, 16));

    var reachOutToUsDescription = createReachOutToUsPageText(font, project);

    var reachOutToUsPane = new JScrollPane(reachOutToUsDescription);
    reachOutToUsPane.setBorder(null);
    reachOutToUsPane.setPreferredSize(new Dimension(SonarLintWalkthroughToolWindow.WIDTH, 100));

    var reachOutToUsBackButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    reachOutToUsBackButtonPanel.add(reachOutToUsBackButton);

    var closeButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    closeButtonPanel.add(closeButton);

    createReachOutToUsPageLayout(reachOutToUsStepLabel, project, reachOutToUsLabel, reachOutToUsPane, reachOutToUsBackButtonPanel,
      closeButtonPanel, reachOutToUsPagePanel, reachOutToUsImageLabel);
  }

  public JPanel getPanel() {
    return reachOutToUsPagePanel;
  }

  private static void createReachOutToUsPageLayout(JLabel stepLabel, Project project, JLabel pageLabel, JScrollPane scrollPane,
    JPanel backButtonPanel, JPanel closeButtonPanel, JPanel panel, JLabel imageLabel) {
    var gbc = new GridBagConstraints();

    var page4CenterPanel = SonarLintWalkthroughUtils.createCenterPanel(stepLabel, pageLabel, scrollPane, gbc);

    gbc.anchor = GridBagConstraints.SOUTHWEST;
    SonarLintWalkthroughUtils.provideCommonButtonConstraints(gbc);
    page4CenterPanel.add(backButtonPanel, gbc);

    gbc.anchor = GridBagConstraints.SOUTHEAST;
    page4CenterPanel.add(closeButtonPanel, gbc);

    panel.add(imageLabel, BorderLayout.NORTH);
    panel.add(page4CenterPanel, BorderLayout.CENTER);
  }

  private static JEditorPane createReachOutToUsPageText(Font font, Project project) {
    var descriptionPane = new JEditorPane(SonarLintWalkthroughToolWindow.EDITOR_PANE_TYPE,
      "<html><body style=\"font-family: " + font.getFamily() +
      "; font-size: " + font.getSize() + "pt; padding: 5px;\">" +
      "You suspect any issue with " + SonarLintWalkthroughToolWindow.SONARQUBE_FOR_IDE + "? Check the <a href=\"#logView\">log view</a>" +
        ".<br>" +
      "Share the verbose logs with us via <a href=\"#communityForum\">Community forum</a> in case of problem. We will be happy to help " +
      "you debug.<br><br>" +
      "Learn more about " + SonarLintWalkthroughToolWindow.SONARQUBE_FOR_IDE + " through <a href=\"#docs\">our docs</a>.</body></html>");

    descriptionPane.setEditable(false);
    descriptionPane.setOpaque(false);
    descriptionPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      public void hyperlinkActivated(HyperlinkEvent e) {
        if ("#logView".equals(e.getDescription())) {
          var sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow(SonarLintWalkthroughToolWindow.SONARQUBE_FOR_IDE);
          if (sonarqubeToolWindow != null) {
            if (!sonarqubeToolWindow.isVisible()) {
              sonarqubeToolWindow.activate(null);
            }
            var currentFileContent = sonarqubeToolWindow.getContentManager().findContent("Log");
            if (currentFileContent != null) {
              sonarqubeToolWindow.getContentManager().setSelectedContent(currentFileContent);
            }
          }
        } else if ("#communityForum".equals(e.getDescription())) {
          BrowserUtil.browse("https://community.sonarsource.com/c/sl/fault/6");
        } else if ("#docs".equals(e.getDescription())) {
          BASE_DOCS_PAGE.browseWithTelemetry();
        }
      }
    });

    return descriptionPane;
  }
}
