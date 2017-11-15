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

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.sonarlint.intellij.util.SonarLintSeverity;

public class SonarLintGlobalOptionsPanel {
  private JPanel rootPane;
  private JCheckBox autoTrigger;

  private ComboBox<SonarLintSeverity> issueSeverity;

  private JCheckBox issueTypeBug;
  private JCheckBox issueTypeCodeSmell;
  private JCheckBox issueTypeVulnerability;

  public SonarLintGlobalOptionsPanel(SonarLintGlobalSettings model) {
    load(model);
  }

  public JComponent getComponent() {
    if (rootPane == null) {

      rootPane = new JPanel(new BorderLayout());
      rootPane.add(createTopPanel(), BorderLayout.NORTH);
      rootPane.add(createIssueFilterPanel(), BorderLayout.WEST);
    }

    return rootPane;
  }

  private JPanel createTopPanel() {
    autoTrigger = new JCheckBox("Automatically trigger analysis");
    autoTrigger.setFocusable(false);
    JPanel tickOptions = new JPanel(new VerticalFlowLayout());
    tickOptions.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    tickOptions.add(autoTrigger);

    return tickOptions;
  }

  private JPanel createIssueFilterPanel() {
    JPanel issueFilterPanel = new JPanel(new GridBagLayout());
    issueFilterPanel.setBorder(IdeBorderFactory.createTitledBorder("Issue filters"));
    JBInsets insets = JBUI.insets(0, 0, 4, 4);

    createIssueSeverityFilter(issueFilterPanel, insets);
    createIssueTypeFilter(issueFilterPanel, insets);

    return issueFilterPanel;
  }

  private void createIssueSeverityFilter(JPanel issueFilterPanel, JBInsets insets) {
    JLabel issueSeverityLabel = new JLabel("Minimum severity:");
    issueSeverity = new ComboBox<>(SonarLintSeverity.values());

    issueFilterPanel.add(issueSeverityLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, 0, insets, 0, 0));
    issueFilterPanel.add(issueSeverity, new GridBagConstraints(1, 0, 3, 1, 0, 0, GridBagConstraints.LINE_START, 0, insets, 0, 0));
  }

  private void createIssueTypeFilter(JPanel issueFilterPanel, JBInsets insets) {
    JLabel issueTypeLabel = new JLabel("Display issue types:");
    issueTypeBug = new JCheckBox("Bug");
    issueTypeBug.setFocusable(false);
    issueTypeCodeSmell = new JCheckBox("Code smell");
    issueTypeCodeSmell.setFocusable(false);
    issueTypeVulnerability = new JCheckBox("Vulnerability");
    issueTypeVulnerability.setFocusable(false);

    issueFilterPanel.add(issueTypeLabel, new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.LINE_START, 0, insets, 0, 0));
    issueFilterPanel.add(issueTypeBug, new GridBagConstraints(1, 1, 1, 1, 0, 0, GridBagConstraints.LINE_START, 0, insets, 0, 0));
    issueFilterPanel.add(issueTypeCodeSmell, new GridBagConstraints(2, 1, 1, 1, 0, 0, GridBagConstraints.LINE_START, 0, insets, 0, 0));
    issueFilterPanel.add(issueTypeVulnerability, new GridBagConstraints(3, 1, 1, 1, 0, 0, GridBagConstraints.LINE_START, 0, insets, 0, 0));
  }

  public boolean isModified(SonarLintGlobalSettings model) {
    getComponent();

    boolean issueTypeFilterChanged = model.isIssueTypeBug() != issueTypeBug.isSelected()
      || model.isIssueTypeCodeSmell() != issueTypeCodeSmell.isSelected()
      || model.isIssueTypeVulnerability() != issueTypeVulnerability.isSelected();

    return model.isAutoTrigger() != autoTrigger.isSelected()
      || model.getIssueSeverity() != issueSeverity.getSelectedItem()
      || issueTypeFilterChanged;
  }

  public void load(SonarLintGlobalSettings model) {
    getComponent();
    autoTrigger.setSelected(model.isAutoTrigger());

    issueSeverity.setSelectedItem(model.getIssueSeverity());

    issueTypeBug.setSelected(model.isIssueTypeBug());
    issueTypeCodeSmell.setSelected(model.isIssueTypeCodeSmell());
    issueTypeVulnerability.setSelected(model.isIssueTypeVulnerability());
  }

  public void save(SonarLintGlobalSettings model) {
    getComponent();
    model.setAutoTrigger(autoTrigger.isSelected());

    model.setIssueSeverity((SonarLintSeverity) issueSeverity.getSelectedItem());

    model.setIssueTypeBug(issueTypeBug.isSelected());
    model.setIssueTypeCodeSmell(issueTypeCodeSmell.isSelected());
    model.setIssueTypeVulnerability(issueTypeVulnerability.isSelected());
  }
}

