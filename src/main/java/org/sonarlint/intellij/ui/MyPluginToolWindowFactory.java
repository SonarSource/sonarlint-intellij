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

import static org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.BASE_DOCS_URL;
import static org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.RULE_SECTION_LINK;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.AI_FIX_SUGGESTIONS_PAGE;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.RULE_SELECTION_PAGE;

public class MyPluginToolWindowFactory implements ToolWindowFactory, DumbAware {

  protected static final String SONARQUBE_FOR_IDE = "SonarQube for IDE";
  protected static final String PAGE_2 = "Page 2";
  protected static final String PAGE_3 = "Page 3";
  protected static final String FONT = "Arial";
  protected static final String EDITOR_PANE_TYPE = "text/html";

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    var font = UIUtil.getLabelFont();
    // Create the content for the tool window
    var mainPanel = new JPanel(new CardLayout());
    var page1 = new JPanel(new BorderLayout());
    var page2 = new JPanel(new BorderLayout());
    var page3 = new JPanel(new BorderLayout());
    var page4 = new JPanel(new BorderLayout());

    // Load the PNG image
    var icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/sonarqube-for-ide-mark.png")));
    var imageLabel1 = new JLabel(icon);
    var imageLabel2 = new JLabel(icon);
    var imageLabel3 = new JLabel(icon);
    var imageLabel4 = new JLabel(icon);

    var titleLabel = new JLabel("Welcome to " + SONARQUBE_FOR_IDE, SwingConstants.CENTER);
    titleLabel.setFont(new Font(FONT, Font.BOLD, 20));
    var paragraphLabel = new JLabel(" ", SwingConstants.CENTER);
    paragraphLabel.setFont(new Font(FONT, Font.PLAIN, 16));
    var descriptionPane1 = createWelcomePageText(font, project);

    var scrollPane1 = new JPanel(new BorderLayout());
    scrollPane1.add(descriptionPane1, BorderLayout.CENTER);
    scrollPane1.setBorder(null);
    scrollPane1.setPreferredSize(new Dimension(300, 150));
    var nextButton1 = new JButton("Next: Learn as you Code");

    var nextButtonPanel1 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    nextButtonPanel1.add(nextButton1);

    createPage1Layout(titleLabel, paragraphLabel, scrollPane1, nextButtonPanel1, page1, imageLabel1);

    // Page 2 content
    var page2Label = new JLabel("Learn as you Code");
    page2Label.setFont(new Font(FONT, Font.PLAIN, 18));

    var descriptionPane1Font = font;

    var descriptionPane2 = createLearnAsYouCodePageText(descriptionPane1Font, project);

    var scrollPane2 = new JScrollPane(descriptionPane2);
    scrollPane2.setBorder(null);
    scrollPane2.setPreferredSize(new Dimension(300, 150));
    var backButton2 = new JButton("Back");
    var nextButton2 = new JButton("Next: Connect with Your Team");

    var backButtonPanel2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
    backButtonPanel2.add(backButton2);

    var nextButtonPanel2 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    nextButtonPanel2.add(nextButton2);

    createPage2Layout(page2Label, scrollPane2, backButtonPanel2, nextButtonPanel2, page2, imageLabel2);

    // Page 3 content
    var page3Label = new JLabel("Connect with Your Team");
    page3Label.setFont(new Font(FONT, Font.PLAIN, 18));

    var descriptionPane3 = createConnectWithYourTeamPageText(descriptionPane1Font, project);

    var scrollPane3 = new JScrollPane(descriptionPane3);
    scrollPane3.setBorder(null);
    scrollPane3.setPreferredSize(new Dimension(300, 150));
    var backButton3 = new JButton("Back");
    var nextButton3 = new JButton("Next: Reach out to us");

    var backButtonPanel3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
    backButtonPanel3.add(backButton3);

    var nextButtonPanel3 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    nextButtonPanel3.add(nextButton3);

    createPage3Layout(page3Label, scrollPane3, backButtonPanel3, nextButtonPanel3, page3, imageLabel3);

    // Page 4 content
    var page4Label = new JLabel("Reach out to us");
    page4Label.setFont(new Font(FONT, Font.PLAIN, 18));

    var descriptionPane4 = createReachOutToUsPageText(descriptionPane1Font, project);

    var scrollPane4 = new JScrollPane(descriptionPane4);
    scrollPane4.setBorder(null);
    scrollPane4.setPreferredSize(new Dimension(300, 150));
    var backButton4 = new JButton("Back");

    var backButtonPanel4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
    backButtonPanel4.add(backButton4);

    createPage4Layout(page4Label, scrollPane4, backButtonPanel4, page4, imageLabel4);

    // Add pages to main panel
    mainPanel.add(page1, "Page 1");
    mainPanel.add(page2, PAGE_2);
    mainPanel.add(page3, PAGE_3);
    mainPanel.add(page4, "Page 4");

    // Add action listeners for buttons
    nextButton1.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, PAGE_2);
    });

    backButton2.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, "Page 1");
    });

    nextButton2.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, PAGE_3);
    });

    backButton3.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, PAGE_2);
    });

    nextButton3.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, "Page 4");
    });

    backButton4.addActionListener(e -> {
      var cl = (CardLayout) (mainPanel.getLayout());
      cl.show(mainPanel, PAGE_3);
    });

    // Add main panel to tool window
    var contentFactory = ContentFactory.getInstance();
    var content = contentFactory.createContent(mainPanel, "", false);
    toolWindow.getContentManager().addContent(content);
  }

  private static void createPage4Layout(JLabel page4Label, JScrollPane scrollPane4, JPanel backButtonPanel4, JPanel page4,
    JLabel imageLabel4) {
    var page4CenterPanel = new JPanel(new GridBagLayout());
    var gbc4 = new GridBagConstraints();
    gbc4.gridx = 0;
    gbc4.gridy = 0;
    gbc4.anchor = GridBagConstraints.NORTH;
    gbc4.fill = GridBagConstraints.NONE;
    page4CenterPanel.add(page4Label, gbc4);

    gbc4.gridy = 1;
    gbc4.fill = GridBagConstraints.BOTH;
    gbc4.weightx = 1.0;
    gbc4.weighty = 1.0;
    page4CenterPanel.add(scrollPane4, gbc4);

    gbc4.gridy = 2;
    gbc4.anchor = GridBagConstraints.SOUTHWEST;
    gbc4.fill = GridBagConstraints.NONE;
    gbc4.weightx = 0;
    gbc4.weighty = 0;
    page4CenterPanel.add(backButtonPanel4, gbc4);

    page4.add(imageLabel4, BorderLayout.NORTH);
    page4.add(page4CenterPanel, BorderLayout.CENTER);
  }

  private static void createPage3Layout(JLabel page3Label, JScrollPane scrollPane3, JPanel backButtonPanel3, JPanel nextButtonPanel3,
    JPanel page3, JLabel imageLabel3) {
    var page3CenterPanel = new JPanel(new GridBagLayout());
    var gbc3 = new GridBagConstraints();
    gbc3.gridx = 0;
    gbc3.gridy = 0;
    gbc3.anchor = GridBagConstraints.NORTH;
    gbc3.fill = GridBagConstraints.NONE;
    page3CenterPanel.add(page3Label, gbc3);

    gbc3.gridy = 1;
    gbc3.fill = GridBagConstraints.BOTH;
    gbc3.weightx = 1.0;
    gbc3.weighty = 1.0;
    page3CenterPanel.add(scrollPane3, gbc3);

    gbc3.gridy = 2;
    gbc3.anchor = GridBagConstraints.SOUTHEAST;
    gbc3.fill = GridBagConstraints.NONE;
    gbc3.weightx = 0;
    gbc3.weighty = 0;
    page3CenterPanel.add(nextButtonPanel3, gbc3);

    gbc3.gridy = 2;
    gbc3.anchor = GridBagConstraints.SOUTHWEST;
    gbc3.fill = GridBagConstraints.NONE;
    gbc3.weightx = 0;
    gbc3.weighty = 0;
    page3CenterPanel.add(backButtonPanel3, gbc3);

    page3.add(imageLabel3, BorderLayout.NORTH);
    page3.add(page3CenterPanel, BorderLayout.CENTER);
  }

  private static void createPage2Layout(JLabel page2Label, JScrollPane scrollPane2, JPanel backButtonPanel2, JPanel nextButtonPanel2,
    JPanel page2, JLabel imageLabel2) {
    var page2CenterPanel = new JPanel(new GridBagLayout());
    var gbc2 = new GridBagConstraints();
    gbc2.gridx = 0;
    gbc2.gridy = 0;
    gbc2.anchor = GridBagConstraints.NORTH;
    gbc2.fill = GridBagConstraints.NONE;
    page2CenterPanel.add(page2Label, gbc2);

    gbc2.gridy = 1;
    gbc2.fill = GridBagConstraints.BOTH;
    gbc2.weightx = 1.0;
    gbc2.weighty = 1.0;
    page2CenterPanel.add(scrollPane2, gbc2);

    gbc2.gridy = 2;
    gbc2.anchor = GridBagConstraints.SOUTHWEST;
    gbc2.fill = GridBagConstraints.NONE;
    gbc2.weightx = 0;
    gbc2.weighty = 0;
    page2CenterPanel.add(backButtonPanel2, gbc2);

    gbc2.gridy = 2;
    gbc2.anchor = GridBagConstraints.SOUTHEAST;
    gbc2.fill = GridBagConstraints.NONE;
    gbc2.weightx = 0;
    gbc2.weighty = 0;
    page2CenterPanel.add(nextButtonPanel2, gbc2);

    page2.add(imageLabel2, BorderLayout.NORTH);
    page2.add(page2CenterPanel, BorderLayout.CENTER);
  }

  private static void createPage1Layout(JLabel titleLabel, JLabel paragraphLabel, JPanel scrollPane1, JPanel nextButtonPanel1,
    JPanel page1, JLabel imageLabel1) {
    var page1CenterPanel = new JPanel(new GridBagLayout());
    var gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.fill = GridBagConstraints.NONE;
    page1CenterPanel.add(titleLabel, gbc);

    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.CENTER;
    page1CenterPanel.add(paragraphLabel, gbc);

    gbc.gridy = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    page1CenterPanel.add(scrollPane1, gbc);

    gbc.gridy = 3;
    gbc.anchor = GridBagConstraints.SOUTHEAST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    gbc.weighty = 0;
    page1CenterPanel.add(nextButtonPanel1, gbc);

    page1.add(imageLabel1, BorderLayout.NORTH);
    page1.add(page1CenterPanel, BorderLayout.CENTER);
  }

  private static @NotNull JEditorPane createReachOutToUsPageText(Font descriptionPane1Font, Project project) {
    var descriptionPane4 = new JEditorPane(EDITOR_PANE_TYPE, "<html><body style=\"font-family: " + descriptionPane1Font.getFamily() +
      "; font-size: " + descriptionPane1Font.getSize() + "pt;\">" +
      "You suspect any issue with " + SONARQUBE_FOR_IDE + "? Check the <a href=\"#logView\">log view</a>.<br>" +
      "Share the verbose logs with us via <a href=\"#communityForum\">Community forum</a> in case of problem. We will be happy to help " +
      "you debug.<br><br>" +
      "Learn more about " + SONARQUBE_FOR_IDE + " through <a href=\"#aiFixSuggestions\">our docs</a>.</body></html>");

    descriptionPane4.setEditable(false);
    descriptionPane4.setOpaque(false);
    descriptionPane4.addHyperlinkListener(new HyperlinkAdapter() {
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
        } else if ("#aiFixSuggestions".equals(e.getDescription())) {
          BrowserUtil.browse(BASE_DOCS_URL);
        }
      }
    });

    return descriptionPane4;
  }

  private static @NotNull JEditorPane createLearnAsYouCodePageText(Font descriptionPane1Font, Project project) {
    var descriptionPane2 = new JEditorPane(EDITOR_PANE_TYPE, "<html><body style=\"font-family: " + descriptionPane1Font.getFamily() +
      "; font-size: " + descriptionPane1Font.getSize() + "pt;\">" +
      "Check the <a href=\"#currentFile\">Current File</a> view: When " + SONARQUBE_FOR_IDE + " found something, click on the issue to " +
      "get the rule " +
      "description and an example of compliant code.<br><br>" +
      "Some rules offer quick fixes when you hover over the issue location.<br><br>" +
      "Finally you can disable rules in the <a href=\"#settings\">settings</a>.</body></html>");

    descriptionPane2.setEditable(false);
    descriptionPane2.setOpaque(false);

    descriptionPane2.addHyperlinkListener(new HyperlinkAdapter() {
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
    return descriptionPane2;
  }

  private static @NotNull JEditorPane createWelcomePageText(Font descriptionPane1Font, Project project) {
    var descriptionPane1 = new JEditorPane(EDITOR_PANE_TYPE, "<html><body style=\"font-family: " + descriptionPane1Font.getFamily() +
      "; font-size: " + descriptionPane1Font.getSize() + "pt;\">" +
      SONARQUBE_FOR_IDE + " supports the analysis of 15+ languages including Python, Java, Javascript, IaC domains along with secrets " +
      "detection. " +
      "<a href=\"" + RULE_SECTION_LINK + "\">Learn more</a>.<br><br>" +
      "Detect issues while you code in an open files or run the analysis on more file in the <a href=\"#reportView\">report view</a>" +
      ".<br><br>" +
      "Open a file and start your clean code journey.</body></html>");

    descriptionPane1.setEditable(false);
    descriptionPane1.setOpaque(false);

    descriptionPane1.addHyperlinkListener(new HyperlinkAdapter() {
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
    return descriptionPane1;
  }

  private static @NotNull JEditorPane createConnectWithYourTeamPageText(Font descriptionPane1Font, Project project) {
    var descriptionPane3 = new JEditorPane(EDITOR_PANE_TYPE, "<html><body style=\"font-family: " + descriptionPane1Font.getFamily() +
      "; font-size: " + descriptionPane1Font.getSize() + "pt;\">" +
      "Apply the same set of rules as your team by using " + SONARQUBE_FOR_IDE + " in Connected Mode with SonarQube Cloud or SonarQube " +
      "Server" +
      ".<br><br>" +
      "With connected mode, benefit from advanced analysis like <a href=\"#taintVulnerabilities\">Taint Vulnerabilities</a> and open " +
      "issues and <a href=\"#aiFixSuggestions\">AI fix suggestions</a> from SonarQube Server or Cloud in the IDE.<br><br>" +
      "Already using SonarQube Cloud or Server? <a href=\"#setupConnection\">Setup a connection</a>.</body></html>");

    descriptionPane3.setEditable(false);
    descriptionPane3.setOpaque(false);

    descriptionPane3.addHyperlinkListener(new HyperlinkAdapter() {
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
    return descriptionPane3;
  }
}

