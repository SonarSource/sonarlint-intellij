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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import icons.SonarLintIcons;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Collections;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.exception.InvalidBindingException;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class AutoTriggerStatusPanel {
  private static final String AUTO_TRIGGER_ENABLED = "AUTO_TRIGGER_ENABLED";
  private static final String FILE_DISABLED = "FILE_DISABLED";
  private static final String AUTO_TRIGGER_DISABLED = "AUTO_TRIGGER_DISABLED";

  private static final String TOOLTIP = "Some files are not automatically analyzed. Check the SonarLint debug logs for details.";

  private final Project project;

  private JPanel panel;
  private CardLayout layout;

  public AutoTriggerStatusPanel(Project project) {
    this.project = project;
    createPanel();
    switchCards();
    CurrentFileStatusPanel.subscribeToEventsThatAffectCurrentFile(project, this::switchCards);
  }

  public JPanel getPanel() {
    return panel;
  }

  private void switchCard(String cardName) {
    ApplicationManager.getApplication().invokeLater(() -> layout.show(panel, cardName), ModalityState.defaultModalityState());
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
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        var localFileExclusions = SonarLintUtils.getService(project, LocalFileExclusions.class);
        try {
          var nonExcluded = localFileExclusions.retainNonExcludedFilesByModules(Collections.singleton(selectedFile), false, (f, r) -> switchCard(FILE_DISABLED));
          if (!nonExcluded.isEmpty()) {
            switchCard(AUTO_TRIGGER_ENABLED);
          }
        } catch (InvalidBindingException e) {
          // not much we can do, analysis won't run anyway. Notification about it was shown by SonarLintEngineManager
          switchCard(AUTO_TRIGGER_ENABLED);
        }
      });
    } else {
      switchCard(AUTO_TRIGGER_ENABLED);
    }
  }

  private void createPanel() {
    layout = new CardLayout();
    panel = new JPanel(layout);

    var gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
      JBUI.insets(2, 2, 2, 2), 0, 0);

    var enabledCard = new JPanel(new GridBagLayout());
    var disabledCard = new JPanel(new GridBagLayout());
    var notThisFileCard = new JPanel(new GridBagLayout());

    var infoIcon = SonarLintIcons.INFO;
    var link = new HyperlinkLabel("");
    link.setIcon(infoIcon);
    link.setUseIconAsLink(true);
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final var label = new JLabel("<html>" + TOOLTIP + "</html>");
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.getInformationColor());
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getSouthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });

    disabledCard.add(new JLabel(SonarLintIcons.WARN), gc);
    notThisFileCard.add(link, gc);

    var enabledLabel = new JLabel("Automatic analysis is enabled");
    var disabledLabel = new JLabel("On-the-fly analysis is disabled - issues are not automatically displayed");
    var notThisFileLabel = new JLabel("This file is not automatically analyzed");
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
