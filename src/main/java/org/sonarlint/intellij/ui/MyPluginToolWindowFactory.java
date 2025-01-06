package org.sonarlint.intellij.ui;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.util.HelpLabelUtils;

import static org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.RULE_SECTION_LINK;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.RULE_SELECTION_PAGE;

public class MyPluginToolWindowFactory implements ToolWindowFactory, DumbAware {

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    var font = UIUtil.getLabelFont();

    JPanel mainPanel = new JPanel(new CardLayout());
    JPanel page1 = new JPanel(new BorderLayout());
    JPanel page2 = new JPanel(new BorderLayout());


    ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/images/sonarqube-for-ide-mark.png")));
    JLabel imageLabel1 = new JLabel(icon);
    JLabel imageLabel2 = new JLabel(icon);


    JLabel titleLabel = new JLabel("Welcome to SonarQube for IDE", SwingConstants.CENTER);
    titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
    JLabel paragraphLabel = new JLabel(" ", SwingConstants.CENTER);
    paragraphLabel.setFont(new Font("Arial", Font.PLAIN, 16));
    var descriptionPane1 = createWelcomePageText(font, project);

    JPanel scrollPane1 = new JPanel(new BorderLayout());
    scrollPane1.add(descriptionPane1, BorderLayout.CENTER);
    scrollPane1.setBorder(null);
    scrollPane1.setPreferredSize(new Dimension(300, 150));
    JButton nextButton = new JButton("Next: Learn as you Code");

    JPanel nextButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    nextButtonPanel.add(nextButton);

    JPanel page1CenterPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
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
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    page1CenterPanel.add(new JPanel(), gbc);

    gbc.gridy = 4;
    gbc.anchor = GridBagConstraints.SOUTHEAST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    gbc.weighty = 0;
    page1CenterPanel.add(nextButtonPanel, gbc);

    page1.add(imageLabel1, BorderLayout.NORTH);
    page1.add(page1CenterPanel, BorderLayout.CENTER);

    JLabel page2Label = new JLabel("Learn as you Code");
    page2Label.setFont(new Font("Arial", Font.PLAIN, 18));

    var descriptionPane1Font = font;

    JEditorPane descriptionPane2 = createLearnAsYouCodePageText(descriptionPane1Font, project);

    JScrollPane scrollPane2 = new JScrollPane(descriptionPane2);
    scrollPane2.setBorder(null);
    JButton backButton = new JButton("Back");

    JPanel backButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    backButtonPanel.add(backButton);

    JPanel page2CenterPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc2 = new GridBagConstraints();
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
    gbc2.fill = GridBagConstraints.BOTH;
    gbc2.weightx = 1.0;
    gbc2.weighty = 1.0;
    page2CenterPanel.add(new JPanel(), gbc2);

    gbc2.gridy = 3;
    gbc2.anchor = GridBagConstraints.SOUTHEAST;
    gbc2.fill = GridBagConstraints.NONE;
    gbc2.weightx = 0;
    gbc2.weighty = 0;
    page2CenterPanel.add(backButtonPanel, gbc2);

    page2.add(imageLabel2, BorderLayout.NORTH);
    page2.add(page2CenterPanel, BorderLayout.CENTER);

    mainPanel.add(page1, "Page 1");
    mainPanel.add(page2, "Page 2");

    nextButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CardLayout cl = (CardLayout) (mainPanel.getLayout());
        cl.show(mainPanel, "Page 2");
      }
    });

    backButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CardLayout cl = (CardLayout) (mainPanel.getLayout());
        cl.show(mainPanel, "Page 1");
      }
    });

    ContentFactory contentFactory = ContentFactory.getInstance();
    Content content = contentFactory.createContent(mainPanel, "", false);
    toolWindow.getContentManager().addContent(content);
  }

  private static @NotNull JEditorPane createLearnAsYouCodePageText(Font descriptionPane1Font, Project project) {
    JEditorPane descriptionPane2 = new JEditorPane("text/html", "<html><body style=\"font-family: " + descriptionPane1Font.getFamily() + "; font-size: " + descriptionPane1Font.getSize() + "pt;\">" +
      "Check the <a href=\"#currentFile\">Current File</a> view: SonarQube for IDE found something. Click on the issue to get the rule description and an example of compliant code.<br><br>" +
      "Some rules offer quick fixes when you hover over the issue location.<br><br>" +
      "Finally you can disable rules in the <a href=\"#settings\">settings</a>.</body></html>");

    descriptionPane2.setEditable(false);
    descriptionPane2.setOpaque(false);

    descriptionPane2.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if ("#currentFile".equals(e.getDescription())) {
          ToolWindow sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow("SonarQube for IDE");
          if (sonarqubeToolWindow != null) {
            if (!sonarqubeToolWindow.isVisible()) {
              sonarqubeToolWindow.activate(null);
            }
            Content currentFileContent = sonarqubeToolWindow.getContentManager().findContent("Current File");
            if (currentFileContent != null) {
              sonarqubeToolWindow.getContentManager().setSelectedContent(currentFileContent);
            }
          }
        } else if ("#settings".equals(e.getDescription())) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, "Tools > SonarQube for IDE");
        } else {
          RULE_SELECTION_PAGE.browseWithTelemetry();
        }
      }
    });
    return descriptionPane2;
  }

  private static @NotNull JEditorPane createWelcomePageText(Font descriptionPane1Font, Project project) {
    JEditorPane descriptionPane1 = new JEditorPane("text/html", "<html><body style=\"font-family: " + descriptionPane1Font.getFamily() + "; font-size: " + descriptionPane1Font.getSize() + "pt;\">" +
      "SonarQube for IDE supports the analysis of 15+ languages including Python, Java, Javascript, IaC domains along with secrets detection. " +
      "<a href=\"" + RULE_SECTION_LINK + "\">Learn more</a>.<br><br>" +
      "Detect issues while you code in an open files or run the analysis on more file in the <a href=\"#reportView\">report view</a>.<br><br>" +
      "Open a file and start your clean code journey.</body></html>");

    descriptionPane1.setEditable(false);
    descriptionPane1.setOpaque(false);

    descriptionPane1.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if ("#reportView".equals(e.getDescription())) {
          ToolWindow sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow("SonarQube for IDE");
          if (sonarqubeToolWindow != null) {
            if (!sonarqubeToolWindow.isVisible()) {
              sonarqubeToolWindow.activate(null);
            }
            Content reportContent = sonarqubeToolWindow.getContentManager().findContent("Report");
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
}

