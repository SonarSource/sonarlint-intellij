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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.FormBuilder;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.core.ConnectionTestTask;
import org.sonarlint.intellij.core.CreateTokenTask;
import org.sonarlint.intellij.util.ResourceLoader;
import org.sonarsource.sonarlint.core.client.api.connected.UnsupportedServerException;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

public class SonarQubeServerEditor extends DialogWrapper {
  private static final Logger LOGGER = Logger.getInstance(SonarQubeServerEditor.class);
  private static final int MAX_LENGTH = 50;
  private static final int TEXT_COLUMNS = 30;
  private static final String AUTH_PASSWORD = "Password";
  private static final String AUTH_TOKEN = "Token";

  private final SonarQubeServer server;
  private final boolean isCreating;
  private final Set<String> serverNames;

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

  protected SonarQubeServerEditor(JComponent parent, List<SonarQubeServer> serverList, SonarQubeServer server, boolean isCreating) {
    super(parent, true);
    this.isCreating = isCreating;
    this.server = server;
    this.serverNames = new HashSet<>();
    for (SonarQubeServer s : serverList) {
      serverNames.add(s.getName());
    }

    if (isCreating) {
      super.setTitle("Create SonarQube server configuration");
    } else {
      super.setTitle("Edit SonarQube server configuration");
    }
    super.setModal(true);
    super.setResizable(true);
    super.init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if(isCreating) {
      return nameText;
    } else {
      return urlText;
    }
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (isCreating) {
      if (StringUtils.isEmpty(nameText.getText())) {
        return new ValidationInfo("Servers must be configured with a name", nameText);
      }

      if (serverNames.contains(nameText.getText())) {
        return new ValidationInfo("Server names must be unique", nameText);
      }
    }

    if (StringUtils.isEmpty(urlText.getText())) {
      return new ValidationInfo("Servers must be configured with a host URL", urlText);
    }

    return null;
  }

  @Nullable @Override protected JComponent createCenterPanel() {
    nameLabel = new JBLabel("Name:", SwingConstants.RIGHT);
    nameLabel.setDisplayedMnemonic('N');
    nameText = new JBTextField();
    nameText.setDocument(new LengthRestrictedDocument(MAX_LENGTH));
    nameText.setText(server.getName());
    if(!isCreating) {
      nameText.setFont(nameText.getFont().deriveFont(Font.BOLD));
    }
    nameText.setEditable(isCreating);
    nameLabel.setLabelFor(nameText);

    urlLabel = new JBLabel("Server URL:", SwingConstants.RIGHT);
    urlLabel.setDisplayedMnemonic('U');
    urlText = new JBTextField();
    urlText.setDocument(new LengthRestrictedDocument(MAX_LENGTH));
    urlText.setText(server.getHostUrl());
    urlText.getEmptyText().setText("Example: http://localhost:9000");
    urlLabel.setLabelFor(urlText);

    authTypeLabel = new JBLabel("Authentication type:", SwingConstants.RIGHT);

    authTypeComboBox = new ComboBox();
    authTypeComboBox.addItem(AUTH_TOKEN);
    authTypeComboBox.addItem(AUTH_PASSWORD);

    loginLabel = new JBLabel("Login:", SwingConstants.RIGHT);
    loginLabel.setDisplayedMnemonic('L');
    loginText = new JBTextField();
    loginText.setDocument(new LengthRestrictedDocument(MAX_LENGTH));
    loginText.setText(server.getLogin());
    loginText.getEmptyText().setText("");
    loginLabel.setLabelFor(loginText);

    passwordLabel = new JBLabel("Password:", SwingConstants.RIGHT);
    passwordText = new JBPasswordField();
    passwordText.setDocument(new LengthRestrictedDocument(MAX_LENGTH));
    passwordText.setText(server.getPassword());
    passwordText.getEmptyText().setText("");
    passwordLabel.setLabelFor(passwordText);

    tokenLabel = new JBLabel("Token:", SwingConstants.RIGHT);
    tokenText = new JBPasswordField();
    tokenText.setDocument(new LengthRestrictedDocument(MAX_LENGTH));
    tokenText.setColumns(TEXT_COLUMNS);
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

    authTypeComboBox.addItemListener(new ItemListener() {
      @Override public void itemStateChanged(ItemEvent e) {
        switchAuth(e.getItem().equals(AUTH_TOKEN));
      }
    });
    setIcon();

    return rootPanel;
  }

  private void setIcon() {
    try {
      ImageIcon sonarQubeIcon = ResourceLoader.getIcon(ResourceLoader.ICON_SONARQUBE_32);
      super.getPeer().getWindow().setIconImage(sonarQubeIcon.getImage());
    } catch (Exception e) {
      // ignore and don't set icon
    }
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
    SonarQubeServer tmpServer = new SonarQubeServer();
    setServer(tmpServer);
    ConnectionTestTask test = new ConnectionTestTask(tmpServer);
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

  @Override
  protected void doOKAction() {
    super.doOKAction();
    setServer(server);
  }

  private void setServer(SonarQubeServer server) {
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

    String title = "Create authentication token";
    if (ex != null) {
      if (ex instanceof UnsupportedServerException) {
        Messages.showErrorDialog(rootPanel, "Failed to create token. Tokens are not supported in the SonarQube server. " + ex.getMessage(), title);
      } else if (ex.getMessage() != null) {
        Messages.showErrorDialog(rootPanel, "Failed to create token: " + ex.getMessage(), title);
      } else {
        Messages.showErrorDialog(rootPanel, "Failed to create token", title);
        LOGGER.info(ex);
      }
    } else {
      tokenText.setText(token);
      Messages.showInfoMessage(rootPanel, "Token created successfully", title);
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
