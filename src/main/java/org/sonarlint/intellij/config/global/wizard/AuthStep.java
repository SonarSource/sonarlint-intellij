/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressResult;
import com.intellij.openapi.progress.impl.ProgressRunner;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import java.awt.CardLayout;
import java.awt.event.ItemEvent;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.credentials.CredentialsService;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.tasks.ConnectionTestTask;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.ProgressUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ThreadUtilsKt.computeOnPooledThread;

public class AuthStep extends AbstractWizardStepEx {
  private static final String LOGIN_ITEM = "Login / Password";
  private static final String TOKEN_ITEM = "Token";
  private final ConnectionWizardModel model;

  private JPanel panel;
  private JComboBox<String> authComboBox;
  private JPasswordField tokenField;
  private JTextField loginField;
  private JPasswordField passwordField;
  private JPanel cardPanel;
  private JButton openTokenCreationPageButton;
  private JLabel deprecated;
  private final ErrorPainter errorPainter;
  private boolean nextStepTriggered = false;

  public AuthStep(ConnectionWizardModel model) {
    super("Authentication");
    this.model = model;

    var comboBoxModel = new DefaultComboBoxModel<String>();
    comboBoxModel.addElement(TOKEN_ITEM);
    if (model.getLogin() != null) {
      authComboBox.setEnabled(false);
      comboBoxModel.addElement(LOGIN_ITEM);
    }
    authComboBox.setModel(comboBoxModel);
    authComboBox.addItemListener(e -> {
      if (ItemEvent.SELECTED == e.getStateChange()) {
        CardLayout cl = (CardLayout) (cardPanel.getLayout());
        if (LOGIN_ITEM.equals(e.getItem())) {
          cl.show(cardPanel, "Login");
        } else {
          cl.show(cardPanel, TOKEN_ITEM);
        }
        fireStateChanged();
      }
    });

    openTokenCreationPageButton.addActionListener(evt -> openTokenCreationPage());

    var listener = new DocumentAdapter() {
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
  protected void fireGoNext() {
    nextStepTriggered = true;
    super.fireGoNext();
  }

  @Override
  public void _init() {
    if (model.getServerType() == ConnectionWizardModel.ServerType.SONARCLOUD) {
      authComboBox.setSelectedItem(TOKEN_ITEM);
      authComboBox.setEnabled(false);
    } else {
      if (model.getLogin() != null) {
        authComboBox.setEnabled(true);
        authComboBox.setSelectedItem(LOGIN_ITEM);
      } else {
        authComboBox.setEnabled(false);
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
    deprecated.setForeground(JBColor.red);
    openTokenCreationPageButton.setText("Create token");
  }

  private void save() {
    getService(CredentialsService.class).saveCredentials(Objects.requireNonNull(model.getName()), getCredentials());
  }

  private Either<TokenDto, UsernamePasswordDto> getCredentials() {
    if (LOGIN_ITEM.equals(authComboBox.getSelectedItem())) {
      return Either.forRight(new UsernamePasswordDto(loginField.getText(),
        String.valueOf(passwordField.getPassword())));
    } else {
      return Either.forLeft(new TokenDto(String.valueOf(tokenField.getPassword())));
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
    if (model.getServerType() == ConnectionWizardModel.ServerType.SONARCLOUD) {
      return OrganizationStep.class;
    }
    return NotificationsStep.class;
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

  private void checkConnection() throws CommitStepException {
    var tmpServer = model.createConnectionWithoutOrganization();
    var test = new ConnectionTestTask(tmpServer);
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
      throw new CommitStepException("An unexpected error occurred, please make sure your token and the provided url is correct." +
        " You can refer to logs for more information.");
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
    progressWindow.setTitle("Generating token\u2026");
    Disposer.register(this, progressWindow);
    try {
      ProgressResult<HelpGenerateUserTokenResponse> progressResult = new ProgressRunner<>(pi -> computeOnPooledThread("Generate User Token Task", () -> {
        var future = getService(BackendService.class).helpGenerateUserToken(serverUrl);
        return ProgressUtils.waitForFuture(pi, future);
      }))
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
    // Prevent triggering next step if user already clicked on next
    if (!nextStepTriggered) {
      fireGoNext();
    }
  }
}
