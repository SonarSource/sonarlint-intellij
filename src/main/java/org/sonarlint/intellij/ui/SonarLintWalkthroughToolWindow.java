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

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentFactory;
import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JPanel;

public class SonarLintWalkthroughToolWindow implements ToolWindowFactory, DumbAware {

  protected static final String SONARQUBE_FOR_IDE = "SonarQube for IDE";
  protected static final String PAGE_2 = "Page 2";
  protected static final String PAGE_3 = "Page 3";
  protected static final String FONT = "Arial";
  protected static final String EDITOR_PANE_TYPE = "text/html";
  protected static final String PREVIOUS = "Previous";
  protected static final int WIDTH = 300;
  protected static final int HEIGHT = 200;

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    var mainPanel = new JPanel(new CardLayout());
    mainPanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));

    var welcomePageNextButton = new JButton("Next: Learn as you code");
    var learnAsYouCodePageNextButton = new JButton("Next: Connect with your team");
    var learnAsYouCodePageBackButton = new JButton(PREVIOUS);
    var connectWithYourTeamBackButton = new JButton(PREVIOUS);
    var connectWithYourTeamNextButton = new JButton("Next: Reach out to us");
    var closeButton = new JButton("Close");
    var reachOutToUsBackButton = new JButton(PREVIOUS);

    var welcomePage = new WelcomePage(project, welcomePageNextButton).getPanel();
    var learnAsYouCodePage = new LearnAsYouCodePage(project, learnAsYouCodePageNextButton, learnAsYouCodePageBackButton).getPanel();
    var connectWithYourTeamPage =
      new ConnectWithYourTeamPage(project, connectWithYourTeamNextButton, connectWithYourTeamBackButton).getPanel();
    var reachOutToUsPage = new ReachOutToUsPage(project, reachOutToUsBackButton, closeButton).getPanel();

    mainPanel.add(welcomePage, "Page 1");
    mainPanel.add(learnAsYouCodePage, PAGE_2);
    mainPanel.add(connectWithYourTeamPage, PAGE_3);
    mainPanel.add(reachOutToUsPage, "Page 4");

    addButtonActionListeners(welcomePageNextButton, mainPanel, learnAsYouCodePageBackButton, learnAsYouCodePageNextButton,
      connectWithYourTeamBackButton, connectWithYourTeamNextButton, reachOutToUsBackButton, closeButton, project);

    var contentFactory = ContentFactory.getInstance();
    var content = contentFactory.createContent(mainPanel, "", false);
    toolWindow.getContentManager().addContent(content);
  }

  private static void addButtonActionListeners(JButton welcomePageNextButton, JPanel mainPanel, JButton learnAsYouCodePageBackButton,
    JButton learnAsYouCodePageNextButton, JButton connectWithYourTeamBackButton, JButton connectWithYourTeamNextButton,
    JButton reachOutToUsBackButton, JButton closeButton, Project project) {
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

    closeButton.addActionListener(e -> {
      var toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Welcome to SonarQube for IDE");
      if (toolWindow != null) {
        toolWindow.hide(null);
      }
    });
  }
}

