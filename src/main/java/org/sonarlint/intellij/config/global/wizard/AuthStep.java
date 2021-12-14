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
package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepCancelledException;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;

import java.awt.CardLayout;
import java.awt.event.ItemEvent;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.tasks.ConnectionTestTask;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.serverapi.system.ValidationResult;

public class AuthStep extends AbstractWizardStepEx {
  private static final String LOGIN_ITEM = "Login / Password";
  private static final String TOKEN_ITEM = "Token";
  private final WizardModel model;

  private JPanel panel;
  private JComboBox authComboBox;
  private JTextField tokenField;
  private JTextField loginField;
  private JPasswordField passwordField;
  private JPanel cardPanel;
  private JButton openTokenCreationPageButton;
  private ErrorPainter errorPainter;

  public AuthStep(WizardModel model) {
    super("Authentication");
    this.model = model;

    DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();
    comboBoxModel.addElement(TOKEN_ITEM);
    comboBoxModel.addElement(LOGIN_ITEM);
    authComboBox.setModel(comboBoxModel);
    authComboBox.addItemListener(e -> {
      if (ItemEvent.SELECTED == e.getStateChange()) {
        CardLayout cl = (CardLayout) (cardPanel.getLayout());
        if (LOGIN_ITEM.equals(e.getItem())) {
          cl.show(cardPanel, "Login");
        } else {
          cl.show(cardPanel, "Token");
        }
        fireStateChanged();
      }
    });

    openTokenCreationPageButton.addActionListener(evt -> openTokenCreationPage());

    DocumentListener listener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        fireStateChanged();
      }
    };

    loginField.getDocument().addDocumentListener(listener);
    tokenField.getDocument().addDocumentListener(listener);
    passwordField.getDocument().addDocumentListener(listener);

    errorPainter = new ErrorPainter();
    errorPainter.installOn(panel, this);
  }

  @Override
  public void _init() {
    if (model.getServerType() == WizardModel.ServerType.SONARCLOUD) {
      authComboBox.setSelectedItem(TOKEN_ITEM);
      authComboBox.setEnabled(false);
    } else {
      authComboBox.setEnabled(true);
      if (model.getLogin() != null) {
        authComboBox.setSelectedItem(LOGIN_ITEM);
      } else {
        authComboBox.setSelectedItem(TOKEN_ITEM);
      }
    }

    tokenField.setText(model.getToken());
    loginField.setText(model.getLogin());
    if (model.getPassword() != null) {
      passwordField.setText(new String(model.getPassword()));
    } else {
      passwordField.setText(null);
    }
  }

  private void save() {
    if (authComboBox.getSelectedItem().equals(LOGIN_ITEM)) {
      model.setToken(null);
      model.setLogin(loginField.getText());
      model.setPassword(passwordField.getPassword());
    } else {
      model.setToken(tokenField.getText());
      model.setLogin(null);
      model.setPassword(null);
    }
  }

  @Override
  public JComponent getComponent() {
    return panel;
  }

  @NotNull
  @Override
  public Object getStepId() {
    return AuthStep.class;
  }

  @Nullable
  @Override
  public Object getNextStepId() {
    if (model.getServerType() == WizardModel.ServerType.SONARCLOUD) {
      return OrganizationStep.class;
    }
    if (model.isNotificationsSupported()) {
      return NotificationsStep.class;
    }
    return ConfirmStep.class;
  }

  @Nullable
  @Override
  public Object getPreviousStepId() {
    return ServerStep.class;
  }

  @Override
  public boolean isComplete() {
    if (authComboBox.getSelectedItem().equals(LOGIN_ITEM)) {
      boolean passValid = passwordField.getPassword().length > 0;
      boolean loginValid = !loginField.getText().isEmpty();
      errorPainter.setValid(passwordField, passValid);
      errorPainter.setValid(loginField, loginValid);
      errorPainter.setValid(tokenField, true);
      return passValid && loginValid;
    } else {
      boolean tokenValid = !tokenField.getText().isEmpty();
      errorPainter.setValid(tokenField, tokenValid);
      errorPainter.setValid(loginField, true);
      errorPainter.setValid(passwordField, true);
      return tokenValid;
    }
  }

  @Override
  public void commit(CommitType commitType) throws CommitStepException {
    if (commitType == CommitType.Finish || commitType == CommitType.Next) {
      save();
      checkConnection();
      tryQueryIfNotificationsSupported();
      tryQueryOrganizations();
    }
  }

  private void tryQueryOrganizations() throws CommitStepException {
    try {
      model.queryOrganizations();
    } catch (Exception e) {
      String msg = "Failed to query organizations. Please check the configuration and try again.";
      if (e.getMessage() != null) {
        msg = msg + " Error: " + e.getMessage();
      }
      throw new CommitStepException(msg);
    }
  }

  private void tryQueryIfNotificationsSupported() throws CommitStepException {
    try {
      model.queryIfNotificationsSupported();
    } catch (Exception e) {
      String msg = "Failed to contact the server. Please check the configuration and try again.";
      if (e.getMessage() != null) {
        msg = msg + " Error: " + e.getMessage();
      }
      throw new CommitStepException(msg);
    }
  }

  private void checkConnection() throws CommitStepException {
    ServerConnection tmpServer = model.createConnectionWithoutOrganization();
    ConnectionTestTask test = new ConnectionTestTask(tmpServer);
    String msg = "Failed to connect to the server. Please check the configuration.";
    ValidationResult result;
    try {
      result = ProgressManager.getInstance().run(test);
    } catch (Exception e) {
      GlobalLogOutput.get().logError("Connection test failed", e);
      if (e.getMessage() != null) {
        msg = msg + " Error: " + e.getMessage();
      }
      throw new CommitStepException(msg);
    }
    if (result == null) {
      throw new CommitStepCancelledException();
    } else if (!result.success()) {
      throw new CommitStepException(msg + " Cause: " + result.message());
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (authComboBox.isEnabled()) {
      return authComboBox;
    }
    return tokenField;
  }

  private void createUIComponents() {
    authComboBox = new ComboBox();
  }

  private void openTokenCreationPage() {
    if (!BrowserUtil.isAbsoluteURL(model.getServerUrl())) {
      Messages.showErrorDialog(panel, "Can't launch browser for URL: " + model.getServerUrl(), "Invalid Server URL");
      return;
    }

    StringBuilder url = new StringBuilder(256);
    url.append(model.getServerUrl());

    if (!model.getServerUrl().endsWith("/")) {
      url.append("/");
    }

    url.append("account/security");
    BrowserUtil.browse(url.toString());
  }
}
