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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Consumer;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.FormBuilder;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

import static java.awt.GridBagConstraints.NONE;
import static java.awt.GridBagConstraints.WEST;

public class SonarQubeServerEditorPanel implements Disposable {
  private JBLabel urlLabel;
  private JBTextField urlText;

  private JBLabel nameLabel;
  private JBTextField nameText;

  private JBLabel idLabel;
  private JBTextField idText;

  private JBTextField loginText;
  private JBLabel loginLabel;

  private JBPasswordField passwordText;
  private JBLabel passwordLabel;

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
    nameText.getEmptyText().setText("");
    nameLabel.setLabelFor(nameText);

    idLabel = new JBLabel("Server ID:", SwingConstants.RIGHT);
    idText = new JBTextField();
    idText.setText(server.getServerId());
    idText.getEmptyText().setText("");
    idLabel.setLabelFor(idText);

    urlLabel = new JBLabel("Server URL:", SwingConstants.RIGHT);
    urlLabel.setDisplayedMnemonic('U');
    urlText = new JBTextField();
    urlText.setText(server.getHostUrl());
    urlText.getEmptyText().setText("Example: http://localhost:9000");
    urlLabel.setLabelFor(urlText);

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

    proxySettingsButton = new JButton("Proxy settings");
    enableProxy = new JBCheckBox("Use proxy", server.enableProxy());
    enableProxy.setMnemonic('y');

    enableProxy.setEnabled(HttpConfigurable.getInstance().USE_HTTP_PROXY);

    testButton = new JButton("Test connection");
    testButton.setHorizontalAlignment(SwingConstants.RIGHT);
    testButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
    testButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        testConnection();
      }
    });

    final JPanel p = new JPanel(new GridBagLayout());
    addForm(p);

    proxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HttpConfigurable.editConfigurable(p);
        enableProxy.setEnabled(HttpConfigurable.getInstance().USE_HTTP_PROXY);
        apply();
      }
    });

    installListener(nameText);
    installListener(urlText);
    installListener(loginText);
    installListener(passwordText);
    installListener(enableProxy);
    installListener(idText);

    return p;
  }

  private void addForm(JPanel p) {
    JPanel form = new CustomFormBuilder()
      .setAlignLabelOnRight(true)
      .setFormLeftIndent(5)
      .addLabeledComponent(nameLabel, nameText)
      .addLabeledComponent(idLabel, idText)
      .addLabeledComponent(urlLabel, urlText)
      .addLabeledComponent(loginLabel, loginText)
      .addLabeledComponent(passwordLabel, passwordText)
      .addVerticalGap(5)
      .addSeparator()
      .addLabeledComponent(enableProxy, proxySettingsButton)
      .getPanel();

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    p.add(form, gbc);

    gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTHEAST;
    gbc.gridy = 1;
    gbc.gridx = 0;
    gbc.weighty = 1.0;
    p.add(testButton, gbc);
  }

  private void testConnection() {
    ConnectionTestTask test = new ConnectionTestTask(server);
    ProgressManager.getInstance().run(test);
    ValidationResult r = test.result();

    if(test.getException() != null) {
      Messages.showErrorDialog(testButton, "Error testing connection: " + test.getException().getMessage(), "Error");
    } else if(r.status()) {
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

  void apply() {
    server.setName(nameText.getText().trim());
    server.setServerId(idText.getText().trim());
    server.setHostUrl(urlText.getText().trim());
    server.setLogin(loginText.getText().trim());
    server.setPassword(new String(passwordText.getPassword()));
    server.setEnableProxy(enableProxy.isSelected());

    changeListener.consume(server);
  }

  @Override public void dispose() {
  }

  /**
   * A {@link FormBuilder} that doesn't fill buttons
   */
  private class CustomFormBuilder extends FormBuilder {
    @Override
    protected int getFill(JComponent component) {
      if(component instanceof JButton) {
        return NONE;
      }
      return super.getFill(component);
    }
  }
}
