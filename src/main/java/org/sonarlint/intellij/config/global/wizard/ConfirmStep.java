/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.ui.SwingHelper;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfirmStep extends AbstractWizardStepEx {
  private static final String TEXT1 = "Connection successfully created.";
  private static final String TEXT2 = "Click finish to save your changes and schedule an update of all project bindings.";
  private final WizardModel model;
  private final boolean editing;
  private JPanel panel;
  private JLabel textArea;
  private JLabel textArea2;
  private JCheckBox notificationsCheckBox;
  private JEditorPane notificationsDetails;

  public ConfirmStep(WizardModel model, boolean editing) {
    super("Configuration completed");
    this.model = model;
    this.editing = editing;
  }

  @Override
  public JComponent getComponent() {
    return panel;
  }

  @Override
  public void _init() {
    if (editing) {
      String txt = TEXT1.replace("created", "edited");
      textArea.setText(txt);
    } else {
      textArea.setText(TEXT1);
    }
    textArea2.setText(TEXT2);
    final boolean isSc = model.getServerType() == WizardModel.ServerType.SONARCLOUD;
    final String sqOrSc = isSc ? "SonarCloud" : "SonarQube";
    notificationsCheckBox.setText("Receive notifications from " + sqOrSc);
    notificationsCheckBox.setVisible(model.isNotificationsSupported());
    notificationsCheckBox.setEnabled(model.isNotificationsSupported());
    notificationsCheckBox.setSelected(!model.isNotificationsDisabled());
    final String docUrl = isSc ? "https://sonarcloud.io/documentation/user-guide/sonarlint-notifications/" :
      "https://docs.sonarqube.org/latest/user-guide/sonarlint-notifications/";
    notificationsDetails.setText("You will receive <a href=\"" + docUrl + "\">notifications</a> from " + sqOrSc + " in situations like:\n" +
      "<ul>" +
      "<li>the Quality Gate status of a bound project changes</li>" +
      "<li>the latest analysis of a bound project on " + sqOrSc + " raises new issues assigned to you</li>" +
      "</ul>");
    notificationsDetails.addHyperlinkListener(new BrowserHyperlinkListener());
    notificationsDetails.setVisible(model.isNotificationsSupported());
  }

  @NotNull
  @Override
  public Object getStepId() {
    return ConfirmStep.class;
  }

  @Nullable
  @Override
  public Object getNextStepId() {
    return null;
  }

  @Nullable
  @Override
  public Object getPreviousStepId() {
    if (model.getServerType() == WizardModel.ServerType.SONARCLOUD) {
      return OrganizationStep.class;
    } else {
      return AuthStep.class;
    }
  }

  @Override
  public boolean isComplete() {
    return true;
  }

  @Override
  public void commit(CommitType commitType) {
    model.setNotificationsDisabled(!notificationsCheckBox.isSelected());
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  private void createUIComponents() {
    notificationsDetails = SwingHelper.createHtmlViewer(false, null, null, null);
  }
}
