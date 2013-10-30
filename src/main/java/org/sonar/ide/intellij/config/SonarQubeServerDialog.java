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

import org.sonar.ide.intellij.model.SonarQubeServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.MalformedURLException;
import java.net.URL;

public class SonarQubeServerDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JTextField idTextField;
  private JTextField urlTextField;
  private JButton testConnectionButton;
  private JLabel msgLabel;

  SonarQubeServer server = null;

  public SonarQubeServerDialog() {
    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);

    buttonOK.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onOK();
      }
    });

    buttonCancel.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCancel();
      }
    });

// call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

// call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCancel();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void onOK() {
    SonarQubeServer validatedServer = validateServer();
    if (validatedServer != null) {
      server = validatedServer;
      dispose();
    }
  }

  private SonarQubeServer validateServer() {
    msgLabel.setText("");
    SonarQubeServer correct = new SonarQubeServer();
    correct.setId(idTextField.getText());
    try {
      correct.setUrl(new URL(urlTextField.getText()));
    } catch (MalformedURLException e) {
      error(e.getMessage());
      return null;
    }
    return correct;
  }

  private void error(String msg) {
    msgLabel.setText(msg);
    msgLabel.setForeground(Color.RED);
  }

  private void onCancel() {
    server = null;
    dispose();
  }

  public void setServer(SonarQubeServer server) {
    this.server = server;
    if (server == null) {
      idTextField.setText("");
      urlTextField.setText("");
    } else {
      idTextField.setText(server.getId());
      idTextField.setEditable(false);
      urlTextField.setText(server.getUrl().toString());
    }
  }

  public SonarQubeServer getServer() {
    return server;
  }

  public static void main(String[] args) {
    SonarQubeServerDialog dialog = new SonarQubeServerDialog();
    dialog.pack();
    dialog.setVisible(true);
    System.exit(0);
  }
}
