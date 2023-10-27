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
package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.ui.SwingHelper;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NotificationsStep extends AbstractWizardStepEx {
  private final WizardModel model;
  private final boolean onlyEditNotifications;
  private JPanel panel;
  private JCheckBox notificationsCheckBox;
  private JEditorPane notificationsDetails;

  public NotificationsStep(WizardModel model, boolean onlyEditNotifications) {
    super("Configure Notifications");
    this.model = model;
    this.onlyEditNotifications = onlyEditNotifications;
  }

  @Override
  public JComponent getComponent() {
    return panel;
  }

  @Override
  public void _init() {
    var serverProduct = Objects.requireNonNull(model.getServerProduct());
    final String productName = serverProduct.getProductName();
    notificationsCheckBox.setText("Receive notifications from " + productName);
    notificationsCheckBox.setSelected(!model.isNotificationsDisabled());
    notificationsDetails.setText("You will receive <a href=\"" + serverProduct.getDocumentation().smartNotificationsHelp() +
      "\">notifications</a> from " + productName + " in situations like:\n" +
      "<ul>" +
      "<li>the Quality Gate status of a bound project changes</li>" +
      "<li>the latest analysis of a bound project on " + productName + " raises new issues assigned to you</li>" +
      "</ul>");
    notificationsDetails.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
  }

  @NotNull
  @Override
  public Object getStepId() {
    return NotificationsStep.class;
  }

  @Nullable
  @Override
  public Object getNextStepId() {
    if (onlyEditNotifications) {
      return null;
    }
    return ConfirmStep.class;
  }

  @Nullable
  @Override
  public Object getPreviousStepId() {
    if (onlyEditNotifications) {
      return null;
    }
    if (model.isSonarCloud()) {
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
