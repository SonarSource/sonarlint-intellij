/**
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
package org.sonarlint.intellij.config.global;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Consumer;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.FormBuilder;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.core.ConnectionTestTask;
import org.sonarlint.intellij.core.CreateTokenTask;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

public class SonarQubeServerEditorPanel {
  private static final String AUTH_PASSWORD = "Password";
  private static final String AUTH_TOKEN = "Token";

  private JPanel rootPanel;

  private JBLabel urlLabel;
  private JBTextField urlText;

  private JBLabel nameLabel;
  private JBTextField nameText;

  private JBLabel authTypeLabel;
  private ComboBox authTypeComboBox;

  private JBTextField loginText;
  private JBLabel loginLabel;

  private JBPasswordField passwordText;
  private JBLabel passwordLabel;

  private JBPasswordField tokenText;
  private JBLabel tokenLabel;
  private JButton tokenButton;

  private JBCheckBox enableProxy;
  private JButton proxySettingsButton;

  protected JButton testButton;
  private final Consumer<SonarQubeServer> changeListener;

  private SonarQubeServer server;

  public SonarQubeServerEditorPanel(Consumer<SonarQubeServer> changeListener, SonarQubeServer server) {
    this.changeListener = changeListener;
    this.server = server;
  }

  public JComponent create() {
    nameLabel = new JBLabel("Name:", SwingConstants.RIGHT);
    nameLabel.setDisplayedMnemonic('N');
    nameText = new JBTextField();
    nameText.setText(server.getName());
    nameText.setEditable(false);
    nameLabel.setLabelFor(nameText);

    urlLabel = new JBLabel("Server URL:", SwingConstants.RIGHT);
    urlLabel.setDisplayedMnemonic('U');
    urlText = new JBTextField();
    urlText.setText(server.getHostUrl());
    urlText.getEmptyText().setText("Example: http://localhost:9000");
    urlLabel.setLabelFor(urlText);

    authTypeLabel = new JBLabel("Authentication type:", SwingConstants.RIGHT);

    authTypeComboBox = new ComboBox();
    authTypeComboBox.addItem(AUTH_TOKEN);
    authTypeComboBox.addItem(AUTH_PASSWORD);

    loginLabel = new JBLabel("Login or token:", SwingConstants.RIGHT);
    loginLabel.setDisplayedMnemonic('L');
    loginText = new JBTextField();
    loginText.setText(server.getLogin());
    loginText.getEmptyText().setText("");
    loginLabel.setLabelFor(loginText);

    passwordLabel = new JBLabel("Password:", SwingConstants.RIGHT);
    passwordText = new JBPasswordField();
    passwordText.setText(server.getPassword());
    passwordText.getEmptyText().setText("");
    passwordLabel.setLabelFor(passwordText);

    tokenLabel = new JBLabel("Token:", SwingConstants.RIGHT);
    tokenText = new JBPasswordField();
    tokenText.setText(server.getToken());
    tokenText.getEmptyText().setText("");
    tokenLabel.setLabelFor(tokenText);

    tokenButton = new JButton("Create token");
    tokenButton.addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        generateToken();
      }
    });

    proxySettingsButton = new JButton("Proxy settings");
    enableProxy = new JBCheckBox("Use proxy", server.enableProxy());
    enableProxy.setMnemonic('y');

    enableProxy.setEnabled(HttpConfigurable.getInstance().USE_HTTP_PROXY);

    testButton = new JButton("Test connection");
    testButton.setFont(testButton.getFont().deriveFont(Font.BOLD));
    testButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        testConnection();
      }
    });

    rootPanel = new JPanel(new GridBagLayout());
    proxySettingsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        HttpConfigurable.editConfigurable(rootPanel);
        enableProxy.setEnabled(HttpConfigurable.getInstance().USE_HTTP_PROXY);
        apply();
      }
    });

    createRootPanel();

    if (server.getLogin() != null) {
      authTypeComboBox.setSelectedItem(AUTH_PASSWORD);
      switchAuth(false);
    } else {
      authTypeComboBox.setSelectedItem(AUTH_TOKEN);
      switchAuth(true);
    }

    installListener(nameText);
    installListener(urlText);
    installListener(loginText);
    installListener(tokenText);
    installListener(authTypeComboBox);
    installListener(passwordText);
    installListener(enableProxy);

    authTypeComboBox.addItemListener(new ItemListener() {
      @Override public void itemStateChanged(ItemEvent e) {
        switchAuth(e.getItem() == AUTH_TOKEN);
      }
    });

    return rootPanel;
  }

  private void switchAuth(boolean token) {
    passwordText.setVisible(!token);
    passwordLabel.setVisible(!token);
    loginText.setVisible(!token);
    loginLabel.setVisible(!token);
    tokenText.setVisible(token);
    tokenLabel.setVisible(token);
    tokenButton.setVisible(token);
  }

  private void createRootPanel() {
    JPanel form = createForm();
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    rootPanel.add(form, gbc);

    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridy = 1;
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    rootPanel.add(testButton, gbc);
  }

  private JPanel createForm() {
    ServerFormBuilder builder = new ServerFormBuilder()
      .addLabeledComponent(nameLabel, nameText, true)
      .addLabeledComponent(urlLabel, urlText, true)
      .addLabeledComponent(authTypeLabel, authTypeComboBox, false)
      .addLabeledComponentWithButton(tokenLabel, tokenText, tokenButton)
      .addLabeledComponent(loginLabel, loginText, true)
      .addLabeledComponent(passwordLabel, passwordText, true)
      .addLabeledComponent(enableProxy, proxySettingsButton, false)
      .addSeparator(5);

    return builder.getPanel();
  }

  private void testConnection() {
    ConnectionTestTask test = new ConnectionTestTask(server);
    ProgressManager.getInstance().run(test);
    ValidationResult r = test.result();

    if (test.getException() != null) {
      String msg = "Error testing connection";
      if (test.getException().getMessage() != null) {
        msg = msg + ": " + test.getException().getMessage();
      }
      Messages.showErrorDialog(testButton, msg, "Error");
    } else if (r.success()) {
      Messages.showMessageDialog(testButton, r.message(), "Connection", Messages.getInformationIcon());
    } else {
      Messages.showErrorDialog(testButton, r.message(), "Connection failed");
    }
  }

  protected void installListener(JTextField textField) {
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        apply();
      }
    });
  }

  protected void installListener(JBCheckBox checkBox) {
    checkBox.addChangeListener(new ChangeListener() {
      @Override public void stateChanged(ChangeEvent e) {
        apply();
      }
    });
  }

  protected void installListener(ComboBox comboBox) {
    comboBox.addItemListener(new ItemListener() {
      @Override public void itemStateChanged(ItemEvent e) {
        apply();
      }
    });
  }

  void apply() {
    server.setName(nameText.getText().trim());
    server.setHostUrl(urlText.getText().trim());

    if (authTypeComboBox.getSelectedItem() == AUTH_TOKEN) {
      server.setToken(new String(tokenText.getPassword()));
      server.setLogin(null);
      server.setPassword(null);
    } else {
      server.setToken(null);
      server.setLogin(loginText.getText().trim());
      server.setPassword(new String(passwordText.getPassword()));
    }
    server.setEnableProxy(enableProxy.isSelected());

    changeListener.consume(server);
  }

  private void generateToken() {
    CreateTokenDialog dialog = new CreateTokenDialog(urlText.getText());
    if (!dialog.showAndGet()) {
      return;
    }
    String login = dialog.getLogin();
    char[] password = dialog.getPassword();
    CreateTokenTask createTokenTask = new CreateTokenTask(urlText.getText(), nameText.getText(), login, new String(password));
    ProgressManager.getInstance().run(createTokenTask);

    Exception ex = createTokenTask.getException();
    String token = createTokenTask.getToken();

    if (ex != null && ex.getMessage() != null) {
      Messages.showErrorDialog(rootPanel, "Failed to create token: " + ex.getMessage(), "Create authentication token");
    } else if (ex != null || token == null) {
      Messages.showErrorDialog(rootPanel, "Failed to create token", "Create authentication token");
    } else {
      Messages.showInfoMessage(rootPanel, "Token created successfully", "Create authentication token");
      tokenText.setText(token);
    }
  }

  private class CreateTokenDialog extends DialogWrapper {
    private static final int COLUMNS = 20;
    private JBTextField login;
    private JBPasswordField password;
    private String hostUrl;

    protected CreateTokenDialog(String hostUrl) {
      super(rootPanel, true);
      this.hostUrl = hostUrl;
      super.setTitle("Credentials to create authentication token");
      init();
    }

    @Nullable @Override protected JComponent createCenterPanel() {
      JBTextField host = new JBTextField();
      host.setText(hostUrl);
      host.setEnabled(false);
      host.setColumns(COLUMNS);

      JBLabel hostLabel = new JBLabel("Host URL:");

      login = new JBTextField();
      login.setColumns(COLUMNS);
      password = new JBPasswordField();

      JBLabel tokenLoginLabel = new JBLabel("Login:");
      JBLabel tokenPasswordLabel = new JBLabel("Password:");
      return new FormBuilder()
        .addLabeledComponent(hostLabel, host)
        .addLabeledComponent(tokenLoginLabel, login)
        .addLabeledComponent(tokenPasswordLabel, password).getPanel();
    }

    public String getLogin() {
      return login.getText();
    }

    public char[] getPassword() {
      return password.getPassword();
    }
  }
}
