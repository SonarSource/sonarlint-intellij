/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import icons.SonarLintIcons;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

public class AutoTriggerStatusPanel {
  private static final String AUTO_TRIGGER_ENABLED = "AUTO_TRIGGER_ENABLED";
  private static final String FILE_DISABLED = "FILE_DISABLED";
  private static final String AUTO_TRIGGER_DISABLED = "AUTO_TRIGGER_DISABLED";

  private static final String TOOLTIP = "Some files are not automatically analyzed. For example, "
    + "files that are excluded or Java files that don't belong the project's source root. Power saving mode disables all automatic analysis";

  private final Project project;
  private final SonarLintAppUtils utils;
  private final SonarLintGlobalSettings globalSettings;
  private JPanel panel;
  private CardLayout layout;

  public AutoTriggerStatusPanel(Project project) {
    this.project = project;
    this.utils = SonarLintUtils.get(SonarLintAppUtils.class);
    this.globalSettings = SonarLintUtils.get(SonarLintGlobalSettings.class);
    createPanel();
    switchCards();
    subscribeToEvents();
  }

  public JPanel getPanel() {
    return panel;
  }

  private void subscribeToEvents() {
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override public void applied(List<SonarQubeServer> serverList, boolean autoTrigger) {
        switchCards();
      }
    });
    busConnection.subscribe(PowerSaveMode.TOPIC, this::switchCards);
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        switchCards();
      }
    });
  }

  private void switchCards() {
    String card;

    if (globalSettings.isAutoTrigger()) {
      VirtualFile selectedFile = SonarLintUtils.getSelectedFile(project);
      if (selectedFile != null) {
        Module m = utils.findModuleForFile(selectedFile, project);
        card = SonarLintUtils.shouldAnalyzeAutomatically(selectedFile, m) ? AUTO_TRIGGER_ENABLED : FILE_DISABLED;
      } else {
        card = AUTO_TRIGGER_ENABLED;
      }
    } else {
      card = AUTO_TRIGGER_DISABLED;
    }

    layout.show(panel, card);
  }

  private void createPanel() {
    layout = new CardLayout();
    panel = new JPanel(layout);

    GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
      JBUI.insets(2, 2, 2, 2), 0, 0);

    JPanel enabledCard = new JPanel(new GridBagLayout());
    JPanel disabledCard = new JPanel(new GridBagLayout());
    JPanel notThisFileCard = new JPanel(new GridBagLayout());

    Icon infoIcon = SonarLintIcons.INFO;
    HyperlinkLabel link = new HyperlinkLabel("");
    link.setIcon(infoIcon);
    link.setUseIconAsLink(true);
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final JLabel label = new JLabel("<html>" + TOOLTIP + "</html>");
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.INFORMATION_COLOR);
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getSouthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });

    disabledCard.add(new JLabel(SonarLintIcons.WARN), gc);
    notThisFileCard.add(link, gc);

    JLabel enabledLabel = new JLabel("Automatic analysis is enabled");
    JLabel disabledLabel = new JLabel("On-the-fly analysis is disabled - issues are not automatically displayed");
    JLabel notThisFileLabel = new JLabel("This file is not automatically analyzed");
    notThisFileLabel.setToolTipText(TOOLTIP);

    enabledCard.add(enabledLabel, gc);
    disabledCard.add(disabledLabel, gc);
    notThisFileCard.add(notThisFileLabel, gc);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    enabledCard.add(Box.createHorizontalBox(), gc);
    disabledCard.add(Box.createHorizontalBox(), gc);
    notThisFileCard.add(Box.createHorizontalBox(), gc);

    panel.add(enabledCard, AUTO_TRIGGER_ENABLED);
    panel.add(disabledCard, AUTO_TRIGGER_DISABLED);
    panel.add(notThisFileCard, FILE_DISABLED);
  }
}
