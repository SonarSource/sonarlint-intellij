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
package org.sonarlint.intellij.config.global.rules;

import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.tree.TreePath;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

public class RuleConfigurationPanel implements ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String SPLITTER_KEY = "sonarlint_rule_configuration";

  private final StandaloneSonarLintEngine engine;
  private RulesTreeTable table;
  private JEditorPane descriptionBrowser;
  private JPanel panel;
  private JBScrollPane scrollPane;
  private RulesTreeTableModel model;
  private JButton restoreDefaults;

  public RuleConfigurationPanel(StandaloneSonarLintEngine engine) {
    this.engine = engine;
    createUIComponents();
  }

  @Override public JComponent getComponent() {
    return panel;
  }

  @Override public boolean isModified(SonarLintGlobalSettings settings) {
    Set<String> included = new HashSet<>();
    Set<String> excluded = new HashSet<>();
    collectIncludedAndExcluded(excluded, included);
    return !included.equals(settings.getIncludedRules()) || !excluded.equals(settings.getExcludedRules());
  }

  @Override public void save(SonarLintGlobalSettings settings) {
    Set<String> included = new HashSet<>();
    Set<String> excluded = new HashSet<>();
    collectIncludedAndExcluded(excluded, included);
    settings.setExcludedRules(excluded);
    settings.setIncludedRules(included);
  }

  private void collectIncludedAndExcluded(Set<String> excluded, Set<String> included) {
    RulesTreeNode.Root rootNode = (RulesTreeNode.Root) model.getRoot();
    for (RulesTreeNode.Language lang : rootNode.childrenIterable()) {
      for (RulesTreeNode.Rule rule : lang.childrenIterable()) {
        if (rule.isChanged()) {
          if (rule.isActivated()) {
            included.add(rule.getKey());
          } else {
            excluded.add(rule.getKey());
          }
        }
      }
    }
  }

  @Override public void load(SonarLintGlobalSettings settings) {
    load(ruleDetails -> loadRuleActivation(settings, ruleDetails));
  }

  private void load(Function<RuleDetails, Boolean> ruleActivation) {
    Collection<RuleDetails> allRuleDetails = engine.getAllRuleDetails();
    Map<String, List<RuleDetails>> rulesByLanguage = allRuleDetails.stream().collect(Collectors.groupingBy(RuleDetails::getLanguage));

    RulesTreeNode rootNode = (RulesTreeNode) model.getRoot();
    rootNode.removeAllChildren();

    for (Map.Entry<String, List<RuleDetails>> e : rulesByLanguage.entrySet()) {
      RulesTreeNode languageNode = new RulesTreeNode.Language(e.getKey());
      for (RuleDetails ruleDetails : e.getValue()) {
        languageNode.add(new RulesTreeNode.Rule(ruleDetails, ruleActivation.apply(ruleDetails)));
      }
      rootNode.add(languageNode);
    }

    TreeUtil.sort(rootNode, Comparator.comparing(Object::toString));
    model.reload();
  }

  private static boolean loadRuleActivation(SonarLintGlobalSettings settings, RuleDetails ruleDetails) {
    if (settings.getIncludedRules().contains(ruleDetails.getKey())) {
      return true;
    } else if (settings.getExcludedRules().contains(ruleDetails.getKey())) {
      return false;
    } else {
      return ruleDetails.isActiveByDefault();
    }
  }

  private void setDescription(String html) {
    try {
      descriptionBrowser.read(new StringReader(html), null);
    } catch (IOException e) {
      // ignore
    }
  }

  private void createUIComponents() {
    panel = new JPanel(new GridBagLayout());

    // top button
    restoreDefaults = new JButton("Restore defaults");
    restoreDefaults.addActionListener(l -> model.restoreDefaults());

    GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 0, 0,
      GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(5, 0, 2, 10), 0, 0);
    panel.add(restoreDefaults, gbc);

    // create tree table
    model = new RulesTreeTableModel(new RulesTreeNode.Root());
    table = new RulesTreeTable(model);
    table.setTreeCellRenderer(new RulesTreeTableRenderer());
    table.setRootVisible(false);
    UIUtil.setLineStyleAngled(table.getTree());
    TreeUtil.installActions(table.getTree());
    new TreeSpeedSearch(table.getTree(), treePath -> {
      Object node = treePath.getLastPathComponent();
      return node.toString();
    });

    table.getTree().addTreeSelectionListener(e -> {
      TreePath path = e.getNewLeadSelectionPath();
      if (path != null) {
        Object node = path.getLastPathComponent();
        if (node instanceof RulesTreeNode.Rule) {
          RulesTreeNode.Rule r = (RulesTreeNode.Rule) node;
          setDescription(r.getHtmlDescription());
          return;
        }
      }
      setDescription("Select a rule to see the description");
    });
    scrollPane = new JBScrollPane(table);
    table.getTree().setShowsRootHandles(true);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM + SideBorder.LEFT + SideBorder.TOP));

    // description pane
    descriptionBrowser = new JEditorPane(UIUtil.HTML_MIME, "<html><body></body></html>");
    descriptionBrowser.setEditable(false);
    descriptionBrowser.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 5));
    descriptionBrowser.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    JPanel descriptionPanel = new JPanel(new BorderLayout());
    descriptionPanel.setBorder(IdeBorderFactory.createTitledBorder("Rule description", false,
      new JBInsets(2, 2, 0, 0)));
    descriptionPanel.add(ScrollPaneFactory.createScrollPane(descriptionBrowser), BorderLayout.CENTER);

    JBSplitter mainSplitter = new JBSplitter(false, SPLITTER_KEY, 0.67f);
    mainSplitter.setFirstComponent(scrollPane);
    mainSplitter.setSecondComponent(descriptionPanel);

    gbc = new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0,
      GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insets(5, 0, 2, 10), 0, 0);
    panel.add(mainSplitter, gbc);
  }
}
