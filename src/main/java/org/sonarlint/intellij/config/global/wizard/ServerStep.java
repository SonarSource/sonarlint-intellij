/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.SwingHelper;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.MouseInputAdapter;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.ui.icons.SonarLintIcons;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.promotion.UtmParameters;
import org.sonarlint.intellij.telemetry.LinkTelemetry;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

import static org.sonarlint.intellij.common.util.SonarLintUtils.SONARCLOUD_URL;
import static org.sonarlint.intellij.common.util.SonarLintUtils.US_SONARCLOUD_URL;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE;

public class ServerStep extends AbstractWizardStepEx {
  private static final int NAME_MAX_LENGTH = 50;
  private final ConnectionWizardModel model;
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
  private JEditorPane sonarQubeDescription;
  private JEditorPane sonarCloudDescription;
  private JEditorPane compareProducts;
  private ErrorPainter errorPainter;
  private JRadioButton radioUS;
  private JRadioButton radioEU;
  private JLabel sonarCloudUrl;

  public ServerStep(ConnectionWizardModel model, boolean editing, Collection<String> existingNames) {
    super("Server Details");
    this.model = model;
    this.existingNames = existingNames;
    radioSonarCloud.addChangeListener(e -> selectionChanged());
    radioSonarQube.addChangeListener(e -> selectionChanged());

    DocumentListener listener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        fireStateChanged();
      }
    };
    urlText.getDocument().addDocumentListener(listener);
    nameField.getDocument().addDocumentListener(listener);

    nameField.setToolTipText("Name of this configuration (mandatory field)");

    sonarCloudUrl.setText("<html><b>Choose your region</b></html>");

    var sqMainText = "A <b>self-managed</b> tool that easily integrates into your " +
      "CI/CD pipelines and DevOps platforms to systematically help you deliver <b>good quality and secure code</b>.";
    sonarQubeDescription.setText(sqMainText);

    var cloudMainText = "A <b>Software-as-a-Service (SaaS)</b> tool that easily integrates into cloud DevOps platforms, " +
      "extending the CI/CD workflow to systematically help you deliver <b>good quality and secure code</b>.";
    sonarCloudDescription.setText(cloudMainText);

    initEditorPane(compareProducts, "Explore SonarQube Cloud with our <a href=\"" + SONARCLOUD_FREE_SIGNUP_PAGE.getUrl() + "\">free tier</a>",
      SONARCLOUD_FREE_SIGNUP_PAGE);

    if (!editing) {
      sonarqubeIcon.addMouseListener(new MouseInputAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          super.mouseClicked(e);
          radioSonarQube.setSelected(true);
        }
      });
      sonarcloudIcon.addMouseListener(new MouseInputAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          super.mouseClicked(e);
          radioSonarCloud.setSelected(true);
        }
      });
    }

    proxyButton.addActionListener(evt -> HttpConfigurable.editConfigurable(panel));

    load(editing);
    paintErrors();
  }

  private static void initEditorPane(JEditorPane editorPane, String text, LinkTelemetry linkTelemetry) {
    editorPane.setText(text);
    editorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        linkTelemetry.browseWithTelemetry(UtmParameters.NEW_CONNECTION_PANEL);
      }
    });
  }

  private void paintErrors() {
    errorPainter = new ErrorPainter();
    errorPainter.installOn(panel, this);
  }

  private void load(boolean editing) {
    var isRegionEnabled = getGlobalSettings().isRegionEnabled();

    sonarCloudUrl.setVisible(isRegionEnabled);
    radioEU.setVisible(isRegionEnabled);
    radioUS.setVisible(isRegionEnabled);

    var sqsIcon = SonarLintIcons.ICON_SONARQUBE_SERVER;
    var sqcIcon = SonarLintIcons.ICON_SONARQUBE_CLOUD;

    if (model.getServerType() == ConnectionWizardModel.ServerType.SONARCLOUD || model.getServerType() == null) {
      radioSonarCloud.setSelected(true);
      if (model.getRegion() == SonarCloudRegion.US) {
        radioUS.setSelected(true);
      } else {
        radioEU.setSelected(true);
      }
      if (editing) {
        sqsIcon = SonarLintIcons.toDisabled(sqsIcon);
      }
    } else {
      radioSonarQube.setSelected(true);
      urlText.setText(model.getServerUrl());
      if (editing) {
        sqcIcon = SonarLintIcons.toDisabled(sqcIcon);
      }
    }

    nameField.setText(model.getName());

    if (editing) {
      nameField.setEnabled(false);
      radioSonarQube.setEnabled(false);
      radioSonarCloud.setEnabled(false);
    }

    sonarqubeIcon.setIcon(sqsIcon);
    sonarcloudIcon.setIcon(sqcIcon);
  }

  private void selectionChanged() {
    boolean sq = radioSonarQube.isSelected();

    sonarCloudUrl.setEnabled(!sq);
    radioEU.setEnabled(!sq);
    radioUS.setEnabled(!sq);

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

  @NotNull
  @Override
  public Object getStepId() {
    return ServerStep.class;
  }

  @Nullable
  @Override
  public Object getNextStepId() {
    return AuthStep.class;
  }

  @Nullable
  @Override
  public Object getPreviousStepId() {
    return null;
  }

  @Override
  public boolean isComplete() {
    boolean nameValid = !nameField.getText().trim().isEmpty();
    errorPainter.setValid(nameField, nameValid);
    boolean urlValid = radioSonarCloud.isSelected() || !urlText.getText().trim().isEmpty();
    errorPainter.setValid(urlText, urlValid);

    return nameValid && urlValid;
  }

  @Override
  public void commit(CommitType commitType) throws CommitStepException {
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
      model.setServerType(ConnectionWizardModel.ServerType.SONARCLOUD);
      var isUS = radioUS.isSelected();
      if (isUS) {
        model.setServerUrl(US_SONARCLOUD_URL);
        model.setRegion(SonarCloudRegion.US);
      } else {
        model.setServerUrl(SONARCLOUD_URL);
        model.setRegion(SonarCloudRegion.EU);
      }
    } else {  
      model.setServerType(ConnectionWizardModel.ServerType.SONARQUBE);
      model.setServerUrl(urlText.getText().trim());
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
    sonarcloudIcon = new JLabel(SonarLintIcons.ICON_SONARQUBE_CLOUD);
    sonarqubeIcon = new JLabel(SonarLintIcons.ICON_SONARQUBE_SERVER);
    sonarcloudText = SwingHelper.createHtmlViewer(false, null, null, null);
    sonarqubeText = SwingHelper.createHtmlViewer(false, null, null, null);
    sonarQubeDescription = SwingHelper.createHtmlViewer(false, null, null, null);
    sonarCloudDescription = SwingHelper.createHtmlViewer(false, null, null, null);
    compareProducts = SwingHelper.createHtmlViewer(false, null, null, null);

    var text = new JBTextField();
    text.getEmptyText().setText("Example: http://localhost:9000");
    urlText = text;

    nameField = new JBTextField();
    nameField.setDocument(new LengthRestrictedDocument(NAME_MAX_LENGTH));
  }
}
