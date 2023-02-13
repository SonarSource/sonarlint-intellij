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

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import java.awt.BorderLayout;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.sonarlint.intellij.actions.SonarConfigureProject;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.finding.hotspot.FoundSecurityHotspots;
import org.sonarlint.intellij.finding.hotspot.InvalidBinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.hotspot.NoBinding;
import org.sonarlint.intellij.finding.hotspot.NoIssueSelected;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsStatus;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class SonarLintHotspotsListPanel {
  private final JPanel mainPanel;
  private final JBList<LiveSecurityHotspot> liveHotspotJBList;
  private final Project project;
  private final CardPanel cardPanel;
  private LiveSecurityHotspot selectedHotspot;
  private final SonarLintRulePanel sonarLintRulePanel;
  private static final String NO_BINDING_CARD_ID = "NO_BINDING_CARD";
  private static final String INVALID_BINDING_CARD_ID = "INVALID_BINDING_CARD";
  private static final String NO_ISSUES_CARD_ID = "NO_ISSUES_CARD_ID";
  private static final String HOTSPOTS_LIST = "HOTSPOTS_LIST";
  private final JLabel noSecurityHotspotsLabel = new JLabel("");

  public SonarLintHotspotsListPanel(Project project) {
    this.project = project;
    liveHotspotJBList = new JBList<>();
    liveHotspotJBList.setCellRenderer(new HotspotCellRenderer());

    sonarLintRulePanel = new SonarLintRulePanel(project);
    sonarLintRulePanel.setVisible(false);

    cardPanel = new CardPanel();
    cardPanel.add(centeredLabel(new JLabel("The project is not bound to SonarQube 9.7+"), new ActionLink("Configure binding", new SonarConfigureProject())), NO_BINDING_CARD_ID);
    cardPanel.add(centeredLabel(new JLabel("The project binding is invalid"), new ActionLink("Edit binding", new SonarConfigureProject())), INVALID_BINDING_CARD_ID);
    cardPanel.add(centeredLabel(noSecurityHotspotsLabel, null), NO_ISSUES_CARD_ID);
    cardPanel.add(ScrollPaneFactory.createScrollPane(liveHotspotJBList), HOTSPOTS_LIST);

    mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(cardPanel.getContainer(), BorderLayout.CENTER);
    EditSourceOnDoubleClickHandler.install(liveHotspotJBList, this::navigateToLocation);
    EditSourceOnEnterKeyHandler.install(liveHotspotJBList, this::navigateToLocation);

    liveHotspotJBList.getSelectionModel().addListSelectionListener(event -> setRuleDescription(liveHotspotJBList.getSelectedValue()));
  }

  private void setRuleDescription(LiveSecurityHotspot selectedHotspot) {
    if (selectedHotspot == null) return;
    this.selectedHotspot = selectedHotspot;
    var moduleForFile = ProjectRootManager.getInstance(project).getFileIndex()
      .getModuleForFile(selectedHotspot.psiFile().getVirtualFile());
    sonarLintRulePanel.setVisible(true);
    sonarLintRulePanel.setRuleKeyForSecurityHotspot(moduleForFile, selectedHotspot.getRuleKey(),
      null, selectedHotspot);
    getService(project, EditorDecorator.class).highlightFinding(selectedHotspot);
  }

  public void loadHotspots(Collection<LiveSecurityHotspot> hotspots) {
    var listHotspots = new CollectionListModel<>(hotspots);
    liveHotspotJBList.setModel(listHotspots);
  }

  public JComponent getPanel() {
    return mainPanel;
  }

  private void navigateToLocation() {
    if (selectedHotspot == null || selectedHotspot.getFile() == null) {
      return;
    }

    var range = selectedHotspot.getRange();
    var offset = range == null ? 0 : range.getStartOffset();
    new OpenFileDescriptor(project, selectedHotspot.getFile(), offset).navigate(true);
  }

  private JPanel centeredLabel(JLabel textLabel, @Nullable ActionLink actionLink) {
    var labelPanel = new JPanel(new HorizontalLayout(5));
    labelPanel.add(textLabel, HorizontalLayout.CENTER);
    if (actionLink != null) {
      labelPanel.add(actionLink, HorizontalLayout.CENTER);
    }
    return labelPanel;
  }

  private void showNoHotspotsLabel() {
    ServerConnection serverConnection = null;
    try {
      serverConnection = getService(project, ProjectBindingManager.class).getServerConnection();
    } catch (InvalidBindingException e) {
      // TODO log the exception
    }
    noSecurityHotspotsLabel.setText("No security hotspots found for currently opened files in the latest analysis on " + serverConnection.getProductName() + ".");
    cardPanel.show(NO_ISSUES_CARD_ID);
  }

  public void populate(SecurityHotspotsStatus status) {
    var highlighting = getService(project, EditorDecorator.class);
    highlighting.removeHighlights();
    if (status instanceof NoBinding) {
      cardPanel.show(NO_BINDING_CARD_ID);
      sonarLintRulePanel.setVisible(false);
    } else if (status instanceof InvalidBinding) {
      cardPanel.show(INVALID_BINDING_CARD_ID);
    } else if (status instanceof NoIssueSelected) {
      sonarLintRulePanel.setVisible(false);
    } else if (status instanceof FoundSecurityHotspots) {
      if (status.isEmpty()) {
        showNoHotspotsLabel();
        sonarLintRulePanel.setVisible(false);
      } else {
        cardPanel.show(HOTSPOTS_LIST);
      }
    }

  }

  public SonarLintRulePanel getSonarLintRulePanel() {
    return this.sonarLintRulePanel;
  }


  public void setSelectedSecurityHotspot(LiveSecurityHotspot securityHotspot) {
    liveHotspotJBList.setSelectedValue(securityHotspot, true);
  }
}
