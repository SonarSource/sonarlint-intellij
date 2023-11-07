/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.HyperlinkEvent;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.SonarLintPlugin;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;

import static org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.BASE_DOCS_URL;

public class SonarLintAboutPanel implements ConfigurationPanel<SonarLintTelemetry> {
  private final JPanel panel;
  private JCheckBox enableTelemetryCheckBox;

  public SonarLintAboutPanel() {
    panel = new JPanel(new BorderLayout(0, 20));
    panel.add(createSonarLintPanel(), BorderLayout.NORTH);
    panel.add(createTelemetryPanel(), BorderLayout.CENTER);
  }

  private JComponent createSonarLintPanel() {
    var sonarlintIcon = new JBLabel(SonarLintIcons.SONARLINT_32);
    var plugin = SonarLintUtils.getService(SonarLintPlugin.class);
    var title = new JBLabel("<html><b>SonarLint " + plugin.getVersion() + "</b></html>");
    var linkLabel = new HyperlinkLabel("Documentation");
    linkLabel.addHyperlinkListener(e -> BrowserUtil.browse(BASE_DOCS_URL));
    var copyrightLabel = new JBLabel("<html>&copy; " + LocalDate.now().getYear() + " SonarSource</html>");

    var infoPanel = new JPanel(new GridBagLayout());
    var constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.ipadx = 10;
    constraints.ipady = 5;
    constraints.gridwidth = 2;
    infoPanel.add(title, constraints);
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.ipady = 2;
    constraints.gridwidth = 1;
    constraints.gridheight = 2;
    constraints.fill = GridBagConstraints.VERTICAL;
    infoPanel.add(sonarlintIcon, constraints);
    constraints.gridx = 1;
    constraints.gridheight = 1;
    constraints.fill = GridBagConstraints.NONE;
    infoPanel.add(linkLabel, constraints);
    constraints.gridy = 2;
    constraints.fill = GridBagConstraints.NONE;
    infoPanel.add(copyrightLabel, constraints);

    return infoPanel;
  }

  private JComponent createTelemetryPanel() {
    // tooltip
    final var link = new HyperlinkLabel("");
    link.setTextWithHyperlink("See a <hyperlink>sample of the data</hyperlink>");
    link.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final var label = new JLabel("""
          <html><pre>{
              "days_since_installation": 120,
              "days_of_use": 40,
              "sonarlint_version": "2.9",
              "sonarlint_product": "SonarLint IntelliJ",
              "ide_version": "IntelliJ IDEA 2020.1 (Community Edition)",
              "os": "Linux",
              "arch": "amd64",
              "jre": "11.0.6",
              "nodejs": "11.12.0",
              "connected_mode_used": true,
              "connected_mode_sonarcloud": false,
              "system_time":"2018-06-27T16:31:49.173+01:00",
              "install_time":"2018-02-27T16:30:49.124+01:00",
              "analyses":[{"language":"java","rate_per_duration":{"0-300":100,"300-500":0,"500-1000":0,"1000-2000":0,"2000-4000":0,"4000+":0}}],
              "server_notifications": {
                "count_by_type": {
                  "NEW_ISSUES": {
                    "received": 1,
                    "clicked": 0
                  },
                  "QUALITY_GATE": {
                    "received": 1,
                    "clicked": 0
                  }
                },
                "disabled": false
              },
              "hotspot": {
                "open_in_browser_count": 1,
                "status_changed_count": 2
              },
              "issue": {
                "status_changed_count": 3
              },
              "help_and_feedback": {
                "count_by_link": {
                  "docs": 5,
                  "faq": 4
                }
              },
              "cayc": {
                "new_code_focus": {
                  "enabled": true,
                  "changes": 2
                },
              },
              "show_hotspot": {
                "requests_count": 3
              },
              "show_issue": {
                "requests_count": 4
              },
              "taint_vulnerabilities": {
                "investigated_remotely_count": 1,
                "investigated_locally_count": 4
              },
              "rules": {
                "raised_issues": [
                  "secrets:S6290",
                  "javascript:S3353",
                  "javascript:S1441"
                ],
                "non_default_enabled": [
                  "javascript:S3513"
                ],
                "default_disabled":  [
                  "javascript:S1994"
                ],
                "quick_fix_applied": [
                  "java:S1656",
                  "java:S1872"
                ],
              },
              "intellij": {
                  "jcefSupported": true
              }
          }</pre></html>""");
        label.setOpaque(true);
        var scrollPane = new JBScrollPane(label);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(HintUtil.createHintBorder());
        scrollPane.setBackground(HintUtil.getInformationColor());
        HintManager.getInstance().showHint(scrollPane, RelativePoint.getNorthWestOf(link), HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE, -1);
      }
    });

    // info
    var info = new JBLabel("<html>By sharing anonymous SonarLint usage statistics, you help us understand how SonarLint is used so "
      + "we can improve the plugin to work even better for you. We don't collect source code, IP addresses, or any personally identifying "
      + "information. And we don't share the data with anyone else.</html>");

    // checkbox
    enableTelemetryCheckBox = new JCheckBox("Share anonymous SonarLint statistics");
    enableTelemetryCheckBox.setFocusable(false);
    var tickOptions = new JPanel(new VerticalFlowLayout());
    tickOptions.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    tickOptions.add(enableTelemetryCheckBox);

    // all telemetry together
    var infoPanel = new JPanel(new GridBagLayout());
    infoPanel.setBorder(IdeBorderFactory.createTitledBorder("Statistics"));
    var constraints = new GridBagConstraints();
    constraints.gridheight = 1;
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.anchor = GridBagConstraints.NORTH;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.weightx = 1.0f;

    infoPanel.add(info, constraints);
    constraints.gridy = 1;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    infoPanel.add(link, constraints);
    constraints.gridy = 2;
    constraints.weighty = 1.0f;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.BOTH;
    infoPanel.add(tickOptions, constraints);

    return infoPanel;
  }

  public JComponent getComponent() {
    return panel;
  }

  @Override
  public void load(SonarLintTelemetry telemetry) {
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
