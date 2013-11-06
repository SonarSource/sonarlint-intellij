/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.ide.intellij.model.SonarQubeServer;
import org.sonar.ide.intellij.util.SonarQubeBundle;
import org.sonar.ide.intellij.wsclient.ISonarWSClientFacade;
import org.sonar.ide.intellij.wsclient.WSClientFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.*;

public class SonarQubeServerDialog extends DialogWrapper {

  public static final Logger LOG = Logger.getInstance(SonarQubeServerDialog.class);

  private JPanel contentPane;
  private JTextField idTextField;
  private JTextField urlTextField;
  private JTextField usernameTextField;
  private JPasswordField passwordField;

  private SonarQubeServer server = null;
  java.util.List<SonarQubeServer> otherServers;
  private boolean edit = false;

  public JTextField getUrlTextField() {
    return urlTextField;
  }

  public JTextField getIdTextField() {
    return idTextField;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public SonarQubeServerDialog(@NotNull Component parent, @Nullable SonarQubeServer server, @NotNull java.util.List<SonarQubeServer> otherServers) {
    super(parent, true);
    this.server = server;
    this.otherServers = otherServers;
    init();
    if (server == null) {
      setTitle(SonarQubeBundle.message("sonarqube.settings.server.add.title"));
      idTextField.setText("");
      urlTextField.setText("");
      addUpdateIdListener();
      usernameTextField.setText("");
      passwordField.setText("");
      edit = false;
    } else {
      setTitle(SonarQubeBundle.message("sonarqube.settings.server.edit.title"));
      idTextField.setText(server.getId());
      idTextField.setEditable(false);
      urlTextField.setText(server.getUrl());
      usernameTextField.setText(server.getUsername());
      passwordField.setText(server.getPassword());
      edit = true;
    }
  }

  @Override
  protected ValidationInfo doValidate() {
    this.server = new SonarQubeServer();
    this.server.setId(idTextField.getText());
    this.server.setUrl(urlTextField.getText());
    this.server.setUsername(usernameTextField.getText());
    this.server.setPassword(new String(passwordField.getPassword()));
    if (edit) {
      return SonarQubeSettings.getInstance().validateServer(this.server, this);
    } else {
      return SonarQubeSettings.getInstance().validateNewServer(otherServers, this.server, this);
    }
  }

  private void addUpdateIdListener() {
    urlTextField.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(DocumentEvent e) {
        update();
      }

      public void removeUpdate(DocumentEvent e) {
        update();
      }

      public void insertUpdate(DocumentEvent e) {
        update();
      }

      public void update() {
        try {
          URL url = new URL(urlTextField.getText());
          idTextField.setText(url.getHost());
        } catch (Exception e) {
          // Do nothing
        }
      }
    });
  }

  public SonarQubeServer getServer() {
    return server;
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return new Action[]{new AbstractAction(SonarQubeBundle.message("sonarqube.settings.server.test")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (doValidate() == null) {
          ISonarWSClientFacade.ConnectionTestResult result = WSClientFactory.getInstance().getSonarClient(SonarQubeServerDialog.this.server, false).testConnection();
          switch (result) {
            case OK:
              Messages.showInfoMessage(contentPane, SonarQubeBundle.message("sonarqube.settings.server.test.ok"), SonarQubeBundle.message("sonarqube.settings.server.test"));
              return;
            case AUTHENTICATION_ERROR:
              Messages.showErrorDialog(contentPane, SonarQubeBundle.message("sonarqube.settings.server.test.authko"), SonarQubeBundle.message("sonarqube.settings.server.test"));
              return;
            case CONNECT_ERROR:
              Messages.showErrorDialog(contentPane, SonarQubeBundle.message("sonarqube.settings.server.test.ko"), SonarQubeBundle.message("sonarqube.settings.server.test"));
              return;
            default:
              LOG.error("Unknow status " + result);
          }
        } else {
          initValidation();
        }
      }
    }};
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return urlTextField;
  }
}
