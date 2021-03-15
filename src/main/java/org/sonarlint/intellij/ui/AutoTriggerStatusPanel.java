/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import icons.SonarLintIcons;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class AutoTriggerStatusPanel {
  private static final String AUTO_TRIGGER_ENABLED = "AUTO_TRIGGER_ENABLED";
  private static final String FILE_DISABLED = "FILE_DISABLED";
  private static final String AUTO_TRIGGER_DISABLED = "AUTO_TRIGGER_DISABLED";

  private static final String TOOLTIP = "Some files are not automatically analyzed. Check the SonarLint debug logs for details.";

  private final Project project;
  private final LocalFileExclusions localFileExclusions;

  private JPanel panel;
  private CardLayout layout;

  public AutoTriggerStatusPanel(Project project) {
    this.project = project;
    this.localFileExclusions = new LocalFileExclusions(project);
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
      @Override public void applied(SonarLintGlobalSettings settings) {
        switchCards();
      }
    });
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, s -> switchCards());
    busConnection.subscribe(PowerSaveMode.TOPIC, this::switchCards);
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        switchCards();
      }
    });
  }

  private void switchCards() {
    String card = getCard();
    layout.show(panel, card);
  }

  private String getCard() {
    if (!getGlobalSettings().isAutoTrigger()) {
      return AUTO_TRIGGER_DISABLED;
    }

    VirtualFile selectedFile = SonarLintUtils.getSelectedFile(project);
    if (selectedFile != null) {
      Module m = SonarLintAppUtils.findModuleForFile(selectedFile, project);
      LocalFileExclusions.Result result = localFileExclusions.canAnalyze(selectedFile, m);
      if (result.isExcluded()) {
        return FILE_DISABLED;
      }
      // here module is not null or file would have been already excluded by canAnalyze
      result = localFileExclusions.checkExclusions(selectedFile, m);
      if (result.isExcluded()) {
        return FILE_DISABLED;
      }

      // Module can't be null here, otherwise it's excluded
      if (isExcludedInServer(m, selectedFile)) {
        return FILE_DISABLED;
      }
    }

    return AUTO_TRIGGER_ENABLED;
  }

  private boolean isExcludedInServer(Module m, VirtualFile f) {
    Predicate<VirtualFile> testPredicate = file -> TestSourcesFilter.isTestSources(file, m.getProject());
    try {
      Collection<VirtualFile> afterExclusion = SonarLintUtils.getService(project, ProjectBindingManager.class).getFacade().getExcluded(m, Collections.singleton(f), testPredicate);
      return !afterExclusion.isEmpty();
    } catch (InvalidBindingException e) {
      // not much we can do, analysis won't run anyway. Notification about it was shown by SonarLintEngineManager
      return false;
    }
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
        label.setBackground(HintUtil.getInformationColor());
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
