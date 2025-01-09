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
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable;

import static org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.RULE_SECTION_LINK;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.AI_FIX_SUGGESTIONS_PAGE;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.BASE_DOCS_PAGE;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.RULE_SELECTION_PAGE;

public class SonarLintWalkthroughToolWindow implements ToolWindowFactory, DumbAware {

  protected static final String SONARQUBE_FOR_IDE = "SonarQube for IDE";
  protected static final String PAGE_2 = "Page 2";
  protected static final String PAGE_3 = "Page 3";
  protected static final String FONT = "Arial";
  protected static final String EDITOR_PANE_TYPE = "text/html";
  protected static final String PREVIOUS = "Previous";

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    var font = UIUtil.getLabelFont();
    var mainPanel = new JPanel(new CardLayout());
    mainPanel.setPreferredSize(new Dimension(70, 100));

    var welcomePage = new JPanel(new BorderLayout());
    var learnAsYouCodePage = new JPanel(new BorderLayout());
    var connectWithYourTeamPage = new JPanel(new BorderLayout());
    var reachOutToUsPage = new JPanel(new BorderLayout());

    var icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/sonarqube-for-ide-mark.png")));
    var welcomeImageLabel = new JLabel(icon);
    var learnAsYouCodeImageLabel = new JLabel(icon);
    var connectWithYourTeamImageLabel = new JLabel(icon);
    var reachOutToUsImageLabel = new JLabel(icon);

    var welcomeStepLabel = new JLabel("Step 1/4", SwingConstants.LEFT);
    welcomeStepLabel.setFont(new Font(FONT, Font.PLAIN, 14));

    var titleLabel = new JLabel("Get started", SwingConstants.LEFT);
    titleLabel.setFont(new Font(FONT, Font.BOLD, 16));
    var welcomePageText = createWelcomePageText(font, project);

    var welcomePageScrollPane = new JScrollPane(welcomePageText);
    welcomePageScrollPane.setBorder(null);
    welcomePageScrollPane.setPreferredSize(new Dimension(70, 100));

    var welcomePageNextButton = new JButton("Next: Learn as you code");

    var welcomePageNextButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    welcomePageNextButtonPanel.add(welcomePageNextButton);

    createWelcomePageLayout(welcomeStepLabel, titleLabel, welcomePageScrollPane, welcomePageNextButtonPanel, welcomePage,
      welcomeImageLabel);

    var learnAsYouCodeStepLabel = new JLabel("Step 2/4", SwingConstants.LEFT);
    learnAsYouCodeStepLabel.setFont(new Font(FONT, Font.PLAIN, 14));

    var learnAsYouCodePageLabel = new JLabel("Learn as you code");
    learnAsYouCodePageLabel.setFont(new Font(FONT, Font.BOLD, 16));

    var learnAsYouCodeText = createLearnAsYouCodePageText(font, project);

    var learnAsYouCodeScrollPane = new JScrollPane(learnAsYouCodeText);
    learnAsYouCodeScrollPane.setBorder(null);
    learnAsYouCodeScrollPane.setPreferredSize(new Dimension(70, 100));

    var learnAsYouCodePageBackButton = new JButton(PREVIOUS);
    var learnAsYouCodePageNextButton = new JButton("Next: Connect with your team");

    var learnAsYouCodePageBackButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    learnAsYouCodePageBackButtonPanel.add(learnAsYouCodePageBackButton);

    var learnAsYouCodePageNextButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    learnAsYouCodePageNextButtonPanel.add(learnAsYouCodePageNextButton);

    createLearnAsYouCodePageLayout(learnAsYouCodeStepLabel, learnAsYouCodePageLabel, learnAsYouCodeScrollPane,
      learnAsYouCodePageBackButtonPanel, learnAsYouCodePageNextButtonPanel, learnAsYouCodePage, learnAsYouCodeImageLabel);

    var connectWithYourTeamStepLabel = new JLabel("Step 3/4", SwingConstants.LEFT);
    connectWithYourTeamStepLabel.setFont(new Font(FONT, Font.PLAIN, 14));
    var connectWithYourTeamLabel = new JLabel("Connect with your team");
    connectWithYourTeamLabel.setFont(new Font(FONT, Font.BOLD, 16));

    var connectWithYourTeamDescription = createConnectWithYourTeamPageText(font, project);

    var connectWithYourTeamScrollPane = new JScrollPane(connectWithYourTeamDescription);
    connectWithYourTeamScrollPane.setBorder(null);
    connectWithYourTeamScrollPane.setPreferredSize(new Dimension(70, 100));
    var connectWithYourTeamBackButton = new JButton(PREVIOUS);
    var connectWithYourTeamNextButton = new JButton("Next: Reach out to us");

    var connectWithYourTeamBackButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    connectWithYourTeamBackButtonPanel.add(connectWithYourTeamBackButton);

    var connectWithYourTeamNextButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    connectWithYourTeamNextButtonPanel.add(connectWithYourTeamNextButton);

    createConnectWithYourTeamLayout(connectWithYourTeamStepLabel, connectWithYourTeamLabel,
      connectWithYourTeamScrollPane, connectWithYourTeamBackButtonPanel, connectWithYourTeamNextButtonPanel, connectWithYourTeamPage,
      connectWithYourTeamImageLabel);

    var reachOutToUsStepLabel = new JLabel("Step 4/4", SwingConstants.LEFT);
    reachOutToUsStepLabel.setFont(new Font(FONT, Font.PLAIN, 14));

    var reachOutToUsLAbel = new JLabel("Reach out to us");
    reachOutToUsLAbel.setFont(new Font(FONT, Font.BOLD, 16));

    var reachOutToUsDescription = createReachOutToUsPageText(font, project);

    var reachOutToUsPane = new JScrollPane(reachOutToUsDescription);
    reachOutToUsPane.setBorder(null);
    reachOutToUsPane.setPreferredSize(new Dimension(70, 100));
    var reachOutToUsBackButton = new JButton(PREVIOUS);

    var reachOutToUsBackButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    reachOutToUsBackButtonPanel.add(reachOutToUsBackButton);

    createReachOutToUsPageLayout(reachOutToUsStepLabel, project, reachOutToUsLAbel, reachOutToUsPane, reachOutToUsBackButtonPanel,
      reachOutToUsPage,
      reachOutToUsImageLabel);

    mainPanel.add(welcomePage, "Page 1");
    mainPanel.add(learnAsYouCodePage, PAGE_2);
    mainPanel.add(connectWithYourTeamPage, PAGE_3);
    mainPanel.add(reachOutToUsPage, "Page 4");

    addButtonActionListeners(welcomePageNextButton, mainPanel, learnAsYouCodePageBackButton, learnAsYouCodePageNextButton,
      connectWithYourTeamBackButton, connectWithYourTeamNextButton, reachOutToUsBackButton);

    var contentFactory = ContentFactory.getInstance();
    var content = contentFactory.createContent(mainPanel, "", false);
    toolWindow.getContentManager().addContent(content);
  }

  private static void addButtonActionListeners(JButton welcomePageNextButton, JPanel mainPanel, JButton learnAsYouCodePageBackButton,
    JButton learnAsYouCodePageNextButton, JButton connectWithYourTeamBackButton, JButton connectWithYourTeamNextButton,
    JButton reachOutToUsBackButton) {
    welcomePageNextButton.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, PAGE_2);
    });

    learnAsYouCodePageBackButton.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, "Page 1");
    });

    learnAsYouCodePageNextButton.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, PAGE_3);
    });

    connectWithYourTeamBackButton.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, PAGE_2);
    });

    connectWithYourTeamNextButton.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, "Page 4");
    });

    reachOutToUsBackButton.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, PAGE_3);
    });
  }

  private static void createWelcomePageLayout(JLabel stepLabel, JLabel titleLabel, JScrollPane welcomePageScrollPane,
    JPanel welcomePageNextButtonPanel, JPanel welcomePagePanel, JLabel welcomePageImageLabel) {
    var gbc = new GridBagConstraints();

    var centerPanel = createCenterPanel(stepLabel, titleLabel, welcomePageScrollPane, gbc);

    gbc.anchor = GridBagConstraints.SOUTHEAST;

    provideCommonButtonConstraints(gbc);

    centerPanel.add(welcomePageNextButtonPanel, gbc);

    welcomePagePanel.add(welcomePageImageLabel, BorderLayout.NORTH);
    welcomePagePanel.add(centerPanel, BorderLayout.CENTER);
  }

  private static void provideCommonButtonConstraints(GridBagConstraints gbc) {
    gbc.gridy = 3;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    gbc.weighty = 0;
  }

  private static void createLearnAsYouCodePageLayout(JLabel stepLabel, JLabel label, JScrollPane pane,
    JPanel backButtonPanel, JPanel nextButtonPanel,
    JPanel page, JLabel imageLabel) {
    var gbc = new GridBagConstraints();

    var centerPanel = createCenterPanel(stepLabel, label, pane, gbc);

    gbc.anchor = GridBagConstraints.SOUTHWEST;

    provideCommonButtonConstraints(gbc);

    centerPanel.add(backButtonPanel, gbc);

    gbc.anchor = GridBagConstraints.SOUTHEAST;

    centerPanel.add(nextButtonPanel, gbc);

    page.add(imageLabel, BorderLayout.NORTH);
    page.add(centerPanel, BorderLayout.CENTER);
  }

  private static void createConnectWithYourTeamLayout(JLabel stepLabel, JLabel connectWithYourTeamLabel,
    JScrollPane connectWithYourTeamPane,
    JPanel connectWithYourTeamBackButtonPanel, JPanel connectWithYourTeamNextButtonPanel,
    JPanel page, JLabel imageLabel) {
    var gbc = new GridBagConstraints();

    var page3CenterPanel = createCenterPanel(stepLabel, connectWithYourTeamLabel, connectWithYourTeamPane, gbc);

    gbc.anchor = GridBagConstraints.SOUTHEAST;

    provideCommonButtonConstraints(gbc);

    page3CenterPanel.add(connectWithYourTeamNextButtonPanel, gbc);

    gbc.anchor = GridBagConstraints.SOUTHWEST;

    page3CenterPanel.add(connectWithYourTeamBackButtonPanel, gbc);

    page.add(imageLabel, BorderLayout.NORTH);
    page.add(page3CenterPanel, BorderLayout.CENTER);
  }

  private static void createReachOutToUsPageLayout(JLabel stepLabel, Project project, JLabel pageLabel, JScrollPane scrollPane,
    JPanel backButtonPanel, JPanel panel, JLabel imageLabel) {
    var gbc = new GridBagConstraints();

    var page4CenterPanel = createCenterPanel(stepLabel, pageLabel, scrollPane, gbc);

    var closeButton = new JButton("Close");
    var closeButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    closeButtonPanel.add(closeButton);

    gbc.anchor = GridBagConstraints.SOUTHWEST;

    provideCommonButtonConstraints(gbc);

    page4CenterPanel.add(backButtonPanel, gbc);

    gbc.anchor = GridBagConstraints.SOUTHEAST;

    page4CenterPanel.add(closeButtonPanel, gbc);

    panel.add(imageLabel, BorderLayout.NORTH);
    panel.add(page4CenterPanel, BorderLayout.CENTER);

    closeButton.addActionListener(e -> {
      var toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Welcome to SonarQube for IDE");
      if (toolWindow != null) {
        toolWindow.hide(null);
      }
    });
  }

  private static @NotNull JEditorPane createReachOutToUsPageText(Font font, Project project) {
    var descriptionPane = new JEditorPane(EDITOR_PANE_TYPE, "<html><body style=\"font-family: " + font.getFamily() +
      "; font-size: " + font.getSize() + "pt; padding: 5px;\">" +
      "You suspect any issue with " + SONARQUBE_FOR_IDE + "? Check the <a href=\"#logView\">log view</a>.<br>" +
      "Share the verbose logs with us via <a href=\"#communityForum\">Community forum</a> in case of problem. We will be happy to help " +
      "you debug.<br><br>" +
      "Learn more about " + SONARQUBE_FOR_IDE + " through <a href=\"#docs\">our docs</a>.</body></html>");

    descriptionPane.setEditable(false);
    descriptionPane.setOpaque(false);
    descriptionPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      public void hyperlinkActivated(HyperlinkEvent e) {
        if ("#logView".equals(e.getDescription())) {
          var sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow(SONARQUBE_FOR_IDE);
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

  private static @NotNull JEditorPane createLearnAsYouCodePageText(Font font, Project project) {
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

  private static @NotNull JEditorPane createWelcomePageText(Font font, Project project) {
    var descriptionPane = new JEditorPane(EDITOR_PANE_TYPE, "<html><body style=\"font-family: " + font.getFamily() +
      "; font-size: " + font.getSize() + "pt; padding: 5px;\">" +
      SONARQUBE_FOR_IDE + " supports the analysis of 15+ languages including Python, Java, Javascript, IaC domains along with secrets " +
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
          var sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow(SONARQUBE_FOR_IDE);
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

  private static @NotNull JEditorPane createConnectWithYourTeamPageText(Font font, Project project) {
    var descriptionPane = new JEditorPane(EDITOR_PANE_TYPE, "<html><body style=\"font-family: " + font.getFamily() +
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

  private static @NotNull JPanel createCenterPanel(JLabel stepLabel, JLabel pageLabel, JScrollPane scrollPane, GridBagConstraints gbc) {
    var centerPanel = new JPanel(new GridBagLayout());
    pageLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 0));
    stepLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 0));

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    centerPanel.add(stepLabel, gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    centerPanel.add(pageLabel, gbc);

    gbc.gridy = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    centerPanel.add(scrollPane, gbc);

    return centerPanel;
  }
}

