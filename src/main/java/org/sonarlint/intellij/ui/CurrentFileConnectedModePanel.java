/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.TooltipWithClickableLinks;
import icons.SonarLintIcons;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ModuleBindingManager;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.util.SonarLintActions;
import org.sonarlint.intellij.vcs.VcsService;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class CurrentFileConnectedModePanel {

  private static final String CONNECTED_MODE_DOCUMENTATION_URL = "https://github.com/SonarSource/sonarlint-intellij/wiki/Bind-to-SonarQube-or-SonarCloud";

  private static final String NOT_CONNECTED = "Not Connected";
  private static final String CONNECTED = "Connected";
  private static final String ERROR = "Error";
  private final Project project;

  private JPanel panel;
  private CardLayout layout;
  private JLabel connectedCard;

  CurrentFileConnectedModePanel(Project project) {
    this.project = project;
    createPanel();
    switchCards();
    CurrentFileStatusPanel.subscribeToEventsThatAffectCurrentFile(project, this::switchCards);
    // TODO Also subscribe to branch management service event
  }

  private void createPanel() {
    layout = new CardLayout();
    panel = new JPanel(layout);

    connectedCard = new JLabel(SonarLintIcons.CONNECTED);

    panel.add(createNotConnectedCard(), NOT_CONNECTED);
    panel.add(connectedCard, CONNECTED);
    panel.add(createErrorCard(), ERROR);

    panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    var clickListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ActionManager.getInstance().tryToExecute(
          SonarLintActions.getInstance().configure(), e, panel, null, true
        );
      }
    };
    Stream.of(panel.getComponents()).forEach(c -> c.addMouseListener(clickListener));

    layout.show(panel, NOT_CONNECTED);
  }

  @NotNull
  private static JLabel createNotConnectedCard() {
    var notConnectedCard = new JLabel(SonarLintIcons.NOT_CONNECTED);
    var notConnectedTooltip = new TooltipWithClickableLinks.ForBrowser(notConnectedCard,
      "<h3>Not Connected</h3>" +
        "<p>Click to synchronize your project with SonarQube or SonarCloud.</p>" +
        "<p><a href=\"" + CONNECTED_MODE_DOCUMENTATION_URL + "\">Learn More</a></p>"
    );
    IdeTooltipManager.getInstance().setCustomTooltip(notConnectedCard, notConnectedTooltip);
    return notConnectedCard;
  }

  @NotNull
  private JLabel createErrorCard() {
    var errorCard = new JLabel(SonarLintIcons.CONNECTION_ERROR);
    var errorTooltip = new TooltipWithClickableLinks(errorCard,
      "<h3>Connected Mode Error</h3>" +
        "<p>There was an issue, please check for additional details in the SonarLint Log.</p>" +
        "<p><a href=\"#\">Open Log</a></p>",
      new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          var contentManager = ToolWindowManager.getInstance(project)
            .getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID)
            .getContentManager();
          contentManager.setSelectedContent(contentManager.findContent(SonarLintToolWindowFactory.TAB_LOGS));
        }
      }
    );
    IdeTooltipManager.getInstance().setCustomTooltip(errorCard, errorTooltip);
    return errorCard;
  }

  private void switchCards() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    var selectedFile = SonarLintUtils.getSelectedFile(project);
    if (selectedFile != null) {
      // Checking connected mode state may take time, so lets move from EDT to pooled thread
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        var projectBindingManager = getService(project, ProjectBindingManager.class);
        projectBindingManager.tryGetServerConnection().ifPresentOrElse(serverConnection -> {
          try {
            updateConnectedCard(selectedFile, serverConnection);
            switchCard(CONNECTED);
          } catch(IllegalStateException e) {
            switchCard(ERROR);
          }
        }, () -> switchCard(NOT_CONNECTED));
      });
    } else {
      switchCard(NOT_CONNECTED);
    }
  }

  private void updateConnectedCard(VirtualFile selectedFile, ServerConnection serverConnection) {
    var module = illegalStateIfNull(ModuleUtilCore.findModuleForFile(selectedFile, project),"Could not find module for file " + selectedFile);
    var projectKey = illegalStateIfNull(getService(module, ModuleBindingManager.class).resolveProjectKey(),"Could not find project key for module " + module);
    var branchName = illegalStateIfNull(getService(project, VcsService.class).resolveServerBranchName(module),"Could not match server branch for module " + module);
    var connectedTooltip = new TooltipWithClickableLinks.ForBrowser(connectedCard, buildTooltipHtml(serverConnection, projectKey, branchName));
    IdeTooltipManager.getInstance().setCustomTooltip(connectedCard, connectedTooltip);
  }

  private <T> T illegalStateIfNull(@Nullable T checkForNull, String messageIfNull) {
    if (checkForNull == null) {
      SonarLintConsole.get(project).error(messageIfNull);
      throw new IllegalStateException(messageIfNull);
    }
    return checkForNull;
  }

  private static String buildTooltipHtml(ServerConnection serverConnection, String projectKey, String branchName) {
    var projectOverviewUrl =
      String.format("%s/dashboard?id=%s&branch=%s",
        serverConnection.getHostUrl(),
        URLEncoder.encode(projectKey, StandardCharsets.UTF_8),
        URLEncoder.encode(branchName, StandardCharsets.UTF_8)
      );
    return String.format(
      "<h3>Connected to %s</h3>" +
      "<p>Bound to project '%s' on connection '%s'</p>" +
      "<p>Synchronized with branch '%s'</p>" +
      "<p><a href=\"%s\">Open Project Overview</a></p>",
      serverConnection.getProductName(), escapeHtml(projectKey), escapeHtml(serverConnection.getName()), escapeHtml(branchName), projectOverviewUrl);
  }

  private void switchCard(String cardName) {
    GuiUtils.invokeLaterIfNeeded(() -> layout.show(panel, cardName), ModalityState.defaultModalityState());
  }

  JPanel getPanel() {
    return panel;
  }
}
