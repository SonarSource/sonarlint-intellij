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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class AutoTriggerStatusPanel {

  private static final String AUTO_TRIGGER_ENABLED = "AUTO_TRIGGER_ENABLED";
  private static final String FILE_DISABLED = "FILE_DISABLED";
  private static final String POWER_SAVE_MODE_ENABLED = "POWER_SAVE_MODE_ENABLED";
  private static final String AUTO_TRIGGER_DISABLED = "AUTO_TRIGGER_DISABLED";

  private static final String TOOLTIP = "Some files are not automatically analyzed. Check the SonarLint debug logs for details.";

  private static final String POWER_SAVE_MODE_TOOLTIP = "Disable power save mode for automatic analysis";

  private final Project project;

  private JPanel panel;
  private CardLayout layout;

  public AutoTriggerStatusPanel(Project project) {
    this.project = project;
    createPanel();
    runOnUiThread(project, this::switchCards);
    CurrentFileStatusPanel.subscribeToEventsThatAffectCurrentFile(project, () -> runOnUiThread(project, this::switchCards));
  }

  public JPanel getPanel() {
    return panel;
  }

  private void switchCard(String cardName) {
    runOnUiThread(project, () -> layout.show(panel, cardName));
  }

  private void switchCards() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!getGlobalSettings().isAutoTrigger()) {
      switchCard(AUTO_TRIGGER_DISABLED);
      return;
    }

    var selectedFile = SonarLintUtils.getSelectedFile(project);
    if (selectedFile != null) {
      // Computing server exclusions may take time, so lets move from EDT to pooled thread
      runOnPooledThread(project, () -> {
        if (!getService(BackendService.class).isAlive()) {
          switchCard(AUTO_TRIGGER_DISABLED);
          return;
        }

        if (PowerSaveMode.isEnabled()) {
          switchCard(POWER_SAVE_MODE_ENABLED);
        } else {
          var localFileExclusions = getService(project, LocalFileExclusions.class);
          var nonExcluded = localFileExclusions.retainNonExcludedFilesByModules(Collections.singleton(selectedFile), false, (f, r) -> {
          });

          if (nonExcluded.isEmpty()) {
            switchCard(FILE_DISABLED);
          } else {
            handleExcludedFiles(selectedFile, nonExcluded);
          }
        }
      });
    } else {
      switchCard(AUTO_TRIGGER_ENABLED);
    }
  }

  private void handleExcludedFiles(VirtualFile selectedFile, Map<Module, Collection<VirtualFile>> nonExcluded) {
    var module = SonarLintAppUtils.findModuleForFile(selectedFile, project);
    if (!nonExcluded.isEmpty() && module != null) {
      var files = nonExcluded.get(module);
      var excludedFilesFromServer = getService(BackendService.class).getExcludedFiles(module, files);
      files.removeAll(excludedFilesFromServer);

      if (files.isEmpty()) {
        switchCard(FILE_DISABLED);
        return;
      }
    }
    switchCard(AUTO_TRIGGER_ENABLED);
  }

  private void createPanel() {
    layout = new CardLayout();
    panel = new JPanel(layout);

    var gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
      JBUI.insets(2), 0, 0);

    var enabledCard = new JPanel(new GridBagLayout());
    var disabledCard = new JPanel(new GridBagLayout());
    var notThisFileCard = new JPanel(new GridBagLayout());
    var powerSaveModeCard = new JPanel(new GridBagLayout());

    var infoIcon = SonarLintIcons.INFO;
    var notThisFileLink = getHyperlinkLabel(infoIcon, TOOLTIP);
    var powerSaveModeLink = getHyperlinkLabel(infoIcon, POWER_SAVE_MODE_TOOLTIP);

    disabledCard.add(new JLabel(SonarLintIcons.WARN), gc);
    notThisFileCard.add(notThisFileLink, gc);
    powerSaveModeCard.add(powerSaveModeLink, gc);

    var enabledLabel = new JLabel("Automatic analysis is enabled");
    var disabledLabel = new JLabel("On-the-fly analysis is disabled - issues are not automatically displayed");
    var notThisFileLabel = new JLabel("This file is not automatically analyzed");
    var powerSaveModeLabel = new JLabel("This file is not automatically analyzed because power save mode is enabled");

    notThisFileLabel.setToolTipText(TOOLTIP);
    powerSaveModeLabel.setToolTipText(POWER_SAVE_MODE_TOOLTIP);

    enabledCard.add(enabledLabel, gc);
    disabledCard.add(disabledLabel, gc);
    notThisFileCard.add(notThisFileLabel, gc);
    powerSaveModeCard.add(powerSaveModeLabel, gc);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    enabledCard.add(Box.createHorizontalBox(), gc);
    disabledCard.add(Box.createHorizontalBox(), gc);
    notThisFileCard.add(Box.createHorizontalBox(), gc);
    powerSaveModeCard.add(Box.createHorizontalBox(), gc);

    panel.add(enabledCard, AUTO_TRIGGER_ENABLED);
    panel.add(disabledCard, AUTO_TRIGGER_DISABLED);
    panel.add(notThisFileCard, FILE_DISABLED);
    panel.add(powerSaveModeCard, POWER_SAVE_MODE_ENABLED);
  }

  @NotNull
  private static HyperlinkLabel getHyperlinkLabel(Icon infoIcon, String tooltip) {
    var link = new HyperlinkLabel("");
    link.setIcon(infoIcon);
    link.setUseIconAsLink(true);
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final var label = new JLabel("<html>" + tooltip + "</html>");
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.getInformationColor());
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getSouthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });
    return link;
  }
}
