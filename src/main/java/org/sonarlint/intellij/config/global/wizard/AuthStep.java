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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepCancelledException;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressResult;
import com.intellij.openapi.progress.impl.ProgressRunner;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.DocumentAdapter;
import java.awt.CardLayout;
import java.awt.event.ItemEvent;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.tasks.ConnectionTestTask;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.ProgressUtils;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.validate.ValidateConnectionResponse;

public class AuthStep extends AbstractWizardStepEx {
  private static final String LOGIN_ITEM = "Login / Password";
  private static final String TOKEN_ITEM = "Token";
  private final WizardModel model;

  private JPanel panel;
  private JComboBox<String> authComboBox;
  private JPasswordField tokenField;
  private JTextField loginField;
  private JPasswordField passwordField;
  private JPanel cardPanel;
  private JButton openTokenCreationPageButton;
  private final ErrorPainter errorPainter;

  public AuthStep(WizardModel model) {
    super("Authentication");
    this.model = model;

    var comboBoxModel = new DefaultComboBoxModel<String>();
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
    var credentials = model.getCredentials();
    if (model.isSonarCloud()) {
      authComboBox.setSelectedItem(TOKEN_ITEM);
      authComboBox.setEnabled(false);
    } else {
      authComboBox.setEnabled(true);
      if (credentials != null && credentials.getLogin() != null) {
        authComboBox.setSelectedItem(LOGIN_ITEM);
      } else {
        authComboBox.setSelectedItem(TOKEN_ITEM);
      }
    }

    if (credentials != null) {
      tokenField.setText(credentials.getToken());
      loginField.setText(credentials.getLogin());
      passwordField.setText(credentials.getPassword());
    }

    openTokenCreationPageButton.setText("Create token");
  }

  private void save() {
    if (LOGIN_ITEM.equals(authComboBox.getSelectedItem())) {
      model.setLoginPassword(loginField.getText(), passwordField.getPassword());
    } else {
      model.setToken(String.valueOf(tokenField.getPassword()));
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
    if (model.isSonarCloud()) {
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
    if (LOGIN_ITEM.equals(authComboBox.getSelectedItem())) {
      boolean passValid = passwordField.getPassword().length > 0;
      boolean loginValid = !loginField.getText().isEmpty();
      errorPainter.setValid(passwordField, passValid);
      errorPainter.setValid(loginField, loginValid);
      errorPainter.setValid(tokenField, true);
      return passValid && loginValid;
    } else {
      boolean tokenValid = tokenField.getPassword().length > 0;
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
      var msg = "Failed to query organizations. Please check the configuration and try again.";
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
      var msg = "Failed to contact the server. Please check the configuration and try again.";
      if (e.getMessage() != null) {
        msg = msg + " Error: " + e.getMessage();
      }
      throw new CommitStepException(msg);
    }
  }

  private void checkConnection() throws CommitStepException {
    var partialConnection = model.createPartialConnection();
    var test = new ConnectionTestTask(partialConnection);
    var msg = "Failed to connect to the server. Please check the configuration.";
    ValidateConnectionResponse result;
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
    } else if (!result.isSuccess()) {
      throw new CommitStepException(msg + " Cause: " + result.getMessage());
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

  private void openTokenCreationPage() {
    var serverUrl = model.getServerUrl();
    if (!BrowserUtil.isAbsoluteURL(serverUrl)) {
      Messages.showErrorDialog(panel, "Can't launch browser for URL: " + serverUrl, "Invalid Server URL");
      return;
    }
    var progressWindow = new ProgressWindow(true, false, null, panel, "Cancel");
    progressWindow.setTitle("Generating token...");
    Disposer.register(this, progressWindow);
    try {
      ProgressResult<HelpGenerateUserTokenResponse> progressResult = new ProgressRunner<>(pi -> {
        var future = SonarLintUtils.getService(BackendService.class).helpGenerateUserToken(serverUrl, model.isSonarCloud());
        return ProgressUtils.waitForFuture(pi, future);
      })
        .sync()
        .onThread(ProgressRunner.ThreadToUse.POOLED)
        .withProgress(progressWindow)
        .modal()
        .submitAndGet();
      var result = progressResult.getResult();
      if (result != null) {
        var token = result.getToken();
        if (token != null) {
          handleReceivedToken(token);
        }
      }
    } catch (Exception e) {
      Messages.showErrorDialog(panel, e.getMessage(), "Unable to Generate Token");
    }
  }


  private void handleReceivedToken(String userToken) {
    tokenField.setText(userToken);
    JFrame visibleFrame = WindowManager.getInstance().findVisibleFrame();
    if (visibleFrame != null) {
      visibleFrame.toFront();
    }
    fireGoNext();
  }
}
