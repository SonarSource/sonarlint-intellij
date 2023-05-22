/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.TooltipWithClickableLinks;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.stream.Stream;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.common.vcs.VcsListener;
import org.sonarlint.intellij.common.vcs.VcsService;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ModuleBindingManager;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.documentation.SonarLintDocumentation;
import org.sonarlint.intellij.util.SonarLintActions;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

public class CurrentFileConnectedModePanel {

  private static final String EMPTY = "Empty";
  private static final String NOT_CONNECTED = "Not Connected";
  private static final String CONNECTED = "Connected";
  private static final String ERROR = "Error";
  private final Project project;

  private JPanel panel;
  private CardLayout layout;
  private ConnectedCard connectedCard;

  CurrentFileConnectedModePanel(Project project) {
    this.project = project;
    createPanel();
    switchCards();
    CurrentFileStatusPanel.subscribeToEventsThatAffectCurrentFile(project, this::switchCards);
    project.getMessageBus().connect(project).subscribe(VcsListener.TOPIC,
      (module, branchName) -> runOnUiThread(project, this::switchCards));
  }

  private void createPanel() {
    layout = new CardLayout();
    panel = new JPanel(layout);

    connectedCard = new ConnectedCard();

    panel.add(new EmptyCard(), EMPTY);
    panel.add(new NotConnectedCard(), NOT_CONNECTED);
    panel.add(connectedCard, CONNECTED);
    panel.add(new ErrorCard(), ERROR);

    panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    var clickListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ActionManager.getInstance().tryToExecute(
          SonarLintActions.getInstance().configure(), e, panel, null, true);
      }
    };
    Stream.of(panel.getComponents()).forEach(c -> c.addMouseListener(clickListener));

    layout.show(panel, NOT_CONNECTED);
  }

  static class EmptyCard extends JLabel {
    private EmptyCard() {
      super("");
    }
  }

  static class NotConnectedCard extends JLabel {
    private NotConnectedCard() {
      super(SonarLintIcons.NOT_CONNECTED);
      var notConnectedTooltip = new TooltipWithClickableLinks.ForBrowser(this,
        "<h3>Not Connected</h3>" +
          "<p>Click to synchronize your project with SonarQube or SonarCloud.</p>" +
          "<p><a href=\"" + SonarLintDocumentation.CONNECTED_MODE_LINK + "\">Learn More</a></p>");
      IdeTooltipManager.getInstance().setCustomTooltip(this, notConnectedTooltip);
    }
  }

  class ErrorCard extends JLabel {
    private ErrorCard() {
      super(SonarLintIcons.CONNECTION_ERROR);
      var errorTooltip = new TooltipWithClickableLinks(this,
        "<h3>Connected Mode Error</h3>" +
          "<p>There was an issue, please check for additional details in the SonarLint Log.</p>" +
          "<p><a href=\"#\">Open Log</a></p>",
        new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(HyperlinkEvent e) {
            var contentManager = ToolWindowManager.getInstance(project)
              .getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID)
              .getContentManager();
            contentManager.setSelectedContent(contentManager.findContent(SonarLintToolWindowFactory.LOG_TAB_TITLE));
          }
        });
      IdeTooltipManager.getInstance().setCustomTooltip(this, errorTooltip);
    }
  }

  class ConnectedCard extends JLabel {
    private ConnectedCard() {
      super(SonarLintIcons.CONNECTED);
    }

    private void updateTooltip(Module module, ServerConnection serverConnection) {
      var projectKey = illegalStateIfNull(getService(module, ModuleBindingManager.class).resolveProjectKey(), "Could not find project key for module " + module);
      var branchName = getService(project, VcsService.class).getServerBranchName(module);
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
            var module = SonarLintAppUtils.findModuleForFile(selectedFile, project);
            if (module == null) {
              switchCard(EMPTY);
            } else {
              connectedCard.updateTooltip(module, serverConnection);
              switchCard(CONNECTED);
            }
          } catch (IllegalStateException e) {
            switchCard(ERROR);
          }
        },
          // No connection settings for project
          () -> switchCard(NOT_CONNECTED));
      });
    } else {
      switchCard(EMPTY);
    }
  }

  private static String buildTooltipHtml(ServerConnection serverConnection, String projectKey, @Nullable String branchName) {
    var projectOverviewUrl = String.format("%s/dashboard?id=%s", serverConnection.getHostUrl(), UrlUtils.urlEncode(projectKey));
    var branchParagraph = "<p>Synchronized with the project's main branch</p>";
    if (branchName != null) {
      projectOverviewUrl += String.format("&branch=%s", UrlUtils.urlEncode(branchName));
      branchParagraph = String.format("<p>Synchronized with branch '%s'</p>", escapeHtml(branchName));
    }
    return String.format(
      "<h3>Connected to %s</h3>" +
        "<p>Bound to project '%s' on connection '%s'</p>" +
        "%s" +
        "<p><a href=\"%s\">Open Project Overview</a></p>",
      serverConnection.getProductName(), escapeHtml(projectKey), escapeHtml(serverConnection.getName()), branchParagraph, projectOverviewUrl);
  }

  private void switchCard(String cardName) {
    runOnUiThread(project, () -> layout.show(panel, cardName));
  }

  JPanel getPanel() {
    return panel;
  }
}
