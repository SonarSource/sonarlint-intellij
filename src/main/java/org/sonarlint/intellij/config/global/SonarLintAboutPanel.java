/*
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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import icons.SonarLintIcons;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.SonarApplication;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;

public class SonarLintAboutPanel {
  private final SonarApplication application;
  private JPanel panel;
  private JCheckBox optOutCheckBox;

  public SonarLintAboutPanel(SonarApplication application) {
    this.application = application;
    panel = new JPanel(new BorderLayout(0, 20));
    panel.add(createSonarLintPanel(), BorderLayout.NORTH);
    panel.add(createTelemetryPanel(), BorderLayout.CENTER);
  }

  private JComponent createSonarLintPanel() {
    JBLabel sonarlintIcon = new JBLabel(SonarLintIcons.SONARLINT_32);
    JBLabel title = new JBLabel("<html><b>SonarLint IntelliJ " + application.getVersion() + "</b></html>");
    HyperlinkLabel linkLabel = new HyperlinkLabel("intellij.sonarlint.org");
    linkLabel.addHyperlinkListener(e -> BrowserUtil.browse("http://intellij.sonarlint.org"));
    JBLabel copyrightLabel = new JBLabel("<html>&copy; " + LocalDate.now().getYear() + " SonarSource</html>");

    JPanel infoPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.ipadx = 10;
    c.ipady = 5;
    c.gridwidth = 2;
    infoPanel.add(title, c);
    c.gridx = 0;
    c.gridy = 1;
    c.ipady = 2;
    c.gridwidth = 1;
    c.gridheight = 2;
    c.fill = GridBagConstraints.VERTICAL;
    infoPanel.add(sonarlintIcon, c);
    c.gridx = 1;
    c.gridheight = 1;
    c.fill = GridBagConstraints.NONE;
    infoPanel.add(linkLabel, c);
    c.gridy = 2;
    c.fill = GridBagConstraints.NONE;
    infoPanel.add(copyrightLabel, c);

    return infoPanel;
  }

  private JComponent createTelemetryPanel() {
    // tooltip
    final HyperlinkLabel link = new HyperlinkLabel("");
    link.setHyperlinkText("See an ", "example", " of the data collected");
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final JLabel label = new JLabel("<html><pre>{\n"
          + "    \"days_since_installation\": 120,\n"
          + "    \"days_of_use\": 40,\n"
          + "    \"sonarlint_version\": \"2.9\",\n"
          + "    \"sonarlint_product\": \"SonarLint IntelliJ\",\n"
          + "    \"connected_mode_used\": true\n"
          + "}</pre><html>");
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.INFORMATION_COLOR);
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getNorthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });

    // info
    JBLabel infoIcon = new JBLabel(SonarLintIcons.INFO);
    JBLabel info = new JBLabel("<html>SonarLint collects anonymous usage statistics to help "
      + "us understand how SonarLint is used so that we can continue to improve the product. "
      + "No identifier, source code or IP address is collected.</html>");

    // put everything together
    JPanel infoPanel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.ipadx = 5;
    c.gridheight = 2;
    c.anchor = GridBagConstraints.NORTH;
    c.fill = GridBagConstraints.NONE;
    infoPanel.add(infoIcon, c);

    c.gridheight = 1;
    c.gridx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1.0f;
    infoPanel.add(info, c);

    c.gridx = 1;
    c.gridy = 1;
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    infoPanel.add(link, c);

    // checkbox
    optOutCheckBox = new JCheckBox("Opt out of SonarLint telemetry");
    optOutCheckBox.setFocusable(false);
    JPanel tickOptions = new JPanel(new VerticalFlowLayout());
    tickOptions.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    tickOptions.add(optOutCheckBox);

    // all telemetry together
    JPanel telemetryPanel = new JPanel(new BorderLayout());
    telemetryPanel.setBorder(IdeBorderFactory.createTitledBorder("Telemetry"));
    telemetryPanel.add(infoPanel, BorderLayout.NORTH);
    telemetryPanel.add(tickOptions, BorderLayout.CENTER);

    return telemetryPanel;
  }

  public JComponent getComponent() {
    return panel;
  }

  public void load(boolean telemetryEnabled, boolean telemetryOptedOut) {
    optOutCheckBox.setEnabled(telemetryEnabled);
    optOutCheckBox.setSelected(!telemetryEnabled || telemetryOptedOut);
  }

  public void save(SonarLintTelemetry telemetry) {
    telemetry.optOut(optOutCheckBox.isSelected());
  }

  public boolean isModified(SonarLintTelemetry telemetry) {
    return telemetry.enabled() && telemetry.optedOut() != optOutCheckBox.isSelected();
  }
}
