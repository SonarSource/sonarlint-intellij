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
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.SwingHelper;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.MouseInputAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.SonarProduct;

public class ServerStep extends AbstractWizardStepEx {
  private static final int NAME_MAX_LENGTH = 50;
  private final WizardModel model;
  private final Collection<String> existingNames;

  private JRadioButton radioSonarCloud;
  private JRadioButton radioSonarQube;
  private JPanel panel;
  private JTextField urlText;
  private JLabel urlLabel;
  private JTextField nameField;
  private JLabel sonarcloudIcon;
  private JLabel sonarqubeIcon;
  private JEditorPane sonarcloudText;
  private JEditorPane sonarqubeText;
  private JButton proxyButton;
  private ErrorPainter errorPainter;

  public ServerStep(WizardModel model, boolean editing, Collection<String> existingNames) {
    super("Server Details");
    this.model = model;
    this.existingNames = existingNames;
    radioSonarCloud.addChangeListener(e -> selectionChanged());
    radioSonarQube.addChangeListener(e -> selectionChanged());

    DocumentListener listener = new DocumentAdapter() {
      @Override protected void textChanged(DocumentEvent e) {
        fireStateChanged();
      }
    };
    urlText.getDocument().addDocumentListener(listener);
    nameField.getDocument().addDocumentListener(listener);

    nameField.setToolTipText("Name of this configuration (mandatory field)");

    var cloudText = "Connect to <a href=\"https://sonarcloud.io\">the online service</a>";
    sonarcloudText.setText(cloudText);
    sonarcloudText.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    var sqText = "Connect to a server";
    sonarqubeText.setText(sqText);

    if (!editing) {
      sonarqubeIcon.addMouseListener(new MouseInputAdapter() {
        @Override public void mouseClicked(MouseEvent e) {
          super.mouseClicked(e);
          radioSonarQube.setSelected(true);
        }
      });
      sonarcloudIcon.addMouseListener(new MouseInputAdapter() {
        @Override public void mouseClicked(MouseEvent e) {
          super.mouseClicked(e);
          radioSonarCloud.setSelected(true);
        }
      });
    }

    proxyButton.addActionListener(evt -> HttpConfigurable.editConfigurable(panel));

    load(editing);
    paintErrors();
  }

  private void paintErrors() {
    errorPainter = new ErrorPainter();
    errorPainter.installOn(panel, this);
  }

  private void load(boolean editing) {
    Icon sqIcon = SonarLintIcons.ICON_SONARQUBE;
    Icon clIcon = SonarLintIcons.ICON_SONARCLOUD;

    if (model.getServerProduct() == SonarProduct.SONARCLOUD || model.getServerProduct() == null) {
      radioSonarCloud.setSelected(true);
      if (editing) {
        sqIcon = SonarLintIcons.toDisabled(sqIcon);
      }
    } else {
      radioSonarQube.setSelected(true);
      urlText.setText(model.getServerUrl());
      if (editing) {
        clIcon = SonarLintIcons.toDisabled(clIcon);
      }
    }

    nameField.setText(model.getName());

    if (editing) {
      nameField.setEnabled(false);
      radioSonarQube.setEnabled(false);
      radioSonarCloud.setEnabled(false);
    }

    sonarqubeIcon.setIcon(sqIcon);
    sonarcloudIcon.setIcon(clIcon);
  }

  private void selectionChanged() {
    boolean sq = radioSonarQube.isSelected();

    urlText.setEnabled(sq);
    urlLabel.setEnabled(sq);
    sonarqubeText.setEnabled(sq);
    sonarcloudText.setEnabled(!sq);
    fireStateChanged();
  }

  @Override
  public JComponent getComponent() {
    return panel;
  }

  @NotNull @Override public Object getStepId() {
    return ServerStep.class;
  }

  @Nullable @Override public Object getNextStepId() {
    return AuthStep.class;
  }

  @Nullable @Override public Object getPreviousStepId() {
    return null;
  }

  @Override public boolean isComplete() {
    boolean nameValid = !nameField.getText().trim().isEmpty();
    errorPainter.setValid(nameField, nameValid);
    boolean urlValid = radioSonarCloud.isSelected() || !urlText.getText().trim().isEmpty();
    errorPainter.setValid(urlText, urlValid);

    return nameValid && urlValid;
  }

  @Override public void commit(CommitType commitType) throws CommitStepException {
    validateName();
    validateUrl();
    save();
  }

  private void validateName() throws CommitStepException {
    if (existingNames.contains(nameField.getText().trim())) {
      throw new CommitStepException("There is already a configuration with that name. Please choose another name");
    }
  }

  private void validateUrl() throws CommitStepException {
    if (radioSonarQube.isSelected()) {
      try {
        var url = new URL(urlText.getText());
        if (SonarLintUtils.isBlank(url.getHost())) {
          throw new CommitStepException("Please provide a valid URL");
        }
      } catch (MalformedURLException e) {
        throw new CommitStepException("Please provide a valid URL");
      }
    }
  }

  private void save() {
    if (radioSonarCloud.isSelected()) {
      model.setIsSonarCloud();
    } else {
      model.setIsSonarQube(urlText.getText().trim());
    }
    model.setName(nameField.getText().trim());
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (nameField.isEnabled()) {
      return nameField;
    } else if (urlText.isEnabled()) {
      return urlText;
    }
    return null;
  }

  private void createUIComponents() {
    sonarcloudIcon = new JLabel(SonarLintIcons.ICON_SONARCLOUD);
    sonarqubeIcon = new JLabel(SonarLintIcons.ICON_SONARQUBE);
    sonarcloudText = SwingHelper.createHtmlViewer(false, null, null, null);
    sonarqubeText = SwingHelper.createHtmlViewer(false, null, null, null);

    var text = new JBTextField();
    text.getEmptyText().setText("Example: http://localhost:9000");
    urlText = text;

    nameField = new JBTextField();
    nameField.setDocument(new LengthRestrictedDocument(NAME_MAX_LENGTH));
  }
}
