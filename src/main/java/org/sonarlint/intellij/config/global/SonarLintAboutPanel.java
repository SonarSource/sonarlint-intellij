/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;

public class SonarLintAboutPanel implements ConfigurationPanel<SonarLintTelemetry> {
  private final SonarApplication application;
  private JPanel panel;
  private JCheckBox enableTelemetryCheckBox;

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
    link.setHyperlinkText("See a ", "sample of the data", "");
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final JLabel label = new JLabel("<html><pre>{\n"
          + "    \"days_since_installation\": 120,\n"
          + "    \"days_of_use\": 40,\n"
          + "    \"sonarlint_version\": \"2.9\",\n"
          + "    \"sonarlint_product\": \"SonarLint IntelliJ\",\n"
          + "    \"connected_mode_used\": true\n"
          + "    \"system_time\":\"2018-06-27T16:31:49.173+01:00\",\n"
          + "    \"install_time\":\"2018-02-27T16:30:49.124+01:00\"\n"
          + "    \"analyses\":[{\"language\":\"java\",\"rate_per_duration\":{\"0-300\":100,\"300-500\":0,\"500-1000\":0,\"1000-2000\":0,\"2000-4000\":0,\"4000+\":0}}]\n"
          + "}</pre><html>");
        label.setBorder(HintUtil.createHintBorder());
        label.setBackground(HintUtil.getInformationColor());
        label.setOpaque(true);
        HintManager.getInstance().showHint(label, RelativePoint.getNorthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });

    // info
    JBLabel info = new JBLabel("<html>By sharing anonymous SonarLint usage statistics, you help us understand how SonarLint is used so "
      + "we can improve the plugin to work even better for you. We don't collect source code, IP addresses, or any personally identifying "
      + "information. And we don't share the data with anyone else.</html>");

    // checkbox
    enableTelemetryCheckBox = new JCheckBox("Share anonymous SonarLint statistics");
    enableTelemetryCheckBox.setFocusable(false);
    JPanel tickOptions = new JPanel(new VerticalFlowLayout());
    tickOptions.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    tickOptions.add(enableTelemetryCheckBox);

    // all telemetry together
    JPanel infoPanel = new JPanel(new GridBagLayout());
    infoPanel.setBorder(IdeBorderFactory.createTitledBorder("Statistics"));
    GridBagConstraints c = new GridBagConstraints();
    c.gridheight = 1;
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.NORTH;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1.0f;

    infoPanel.add(info, c);
    c.gridy = 1;
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    infoPanel.add(link, c);
    c.gridy = 2;
    c.weighty = 1.0f;
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.BOTH;
    infoPanel.add(tickOptions, c);

    return infoPanel;
  }

  public JComponent getComponent() {
    return panel;
  }

  @Override
  public void load(SonarLintTelemetry telemetry) {
    enableTelemetryCheckBox.setEnabled(telemetry.canBeEnabled());
    enableTelemetryCheckBox.setSelected(telemetry.enabled());
  }

  @Override
  public void save(SonarLintTelemetry telemetry) {
    telemetry.optOut(!enableTelemetryCheckBox.isSelected());
  }

  @Override
  public boolean isModified(SonarLintTelemetry telemetry) {
    return telemetry.enabled() != enableTelemetryCheckBox.isSelected();
  }
}
