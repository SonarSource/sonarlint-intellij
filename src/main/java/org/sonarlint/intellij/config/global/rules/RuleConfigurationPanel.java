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
package org.sonarlint.intellij.config.global.rules;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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
  private static final String SPLITTER_KEY = "sonarlint_rule_configuration_splitter";

  private final StandaloneSonarLintEngine engine;
  private final RulesFilterModel filterModel = new RulesFilterModel(this::updateModel);
  private RulesTreeTable table;
  private JEditorPane descriptionBrowser;
  private JPanel panel;
  private RulesTreeTableModel model;
  private FilterComponent filterComponent;
  private TreeExpander myTreeExpander;

  private Map<String, Boolean> currentActivationByRuleKey;

  public RuleConfigurationPanel(StandaloneSonarLintEngine engine) {
    this.engine = engine;
    createUIComponents();
  }

  @Override public JComponent getComponent() {
    return panel;
  }

  @Override public boolean isModified(SonarLintGlobalSettings settings) {
    saveCurrentActivation();
    Set<String> included = new HashSet<>();
    Set<String> excluded = new HashSet<>();
    getIncludedAndExcluded(included, excluded);
    return !included.equals(settings.getIncludedRules()) || !excluded.equals(settings.getExcludedRules());
  }

  @Override public void save(SonarLintGlobalSettings settings) {
    saveCurrentActivation();
    Set<String> included = new HashSet<>();
    Set<String> excluded = new HashSet<>();
    getIncludedAndExcluded(included, excluded);
    settings.setExcludedRules(excluded);
    settings.setIncludedRules(included);
  }

  private void getIncludedAndExcluded(Set<String> included, Set<String> excluded) {
    for (RuleDetails r : engine.getAllRuleDetails()) {
      boolean valueSet = currentActivationByRuleKey.get(r.getKey());
      if (r.isActiveByDefault() && !valueSet) {
        excluded.add(r.getKey());
      } else if (!r.isActiveByDefault() && valueSet) {
        included.add(r.getKey());
      }
    }
  }

  private void saveCurrentActivation() {
    model.saveCurrentRuleActivation(currentActivationByRuleKey);
  }

  @Override public void load(SonarLintGlobalSettings settings) {
    filterModel.reset(false);
    filterComponent.reset();

    currentActivationByRuleKey = engine.getAllRuleDetails().stream()
      .collect(Collectors.toMap(RuleDetails::getKey, r -> loadRuleActivation(settings, r)));
    updateModel();
  }

  private void updateModel() {
    saveCurrentActivation();
    Collection<RuleDetails> ruleDetails = engine.getAllRuleDetails();
    Map<String, String> languagesNameByKey = engine.getAllLanguagesNameByKey();
    Map<String, List<RulesTreeNode.Rule>> rulesByLanguage = ruleDetails.stream()
      .map(r -> new RulesTreeNode.Rule(r, currentActivationByRuleKey.get(r.getKey())))
      .filter(filterModel::filter)
      .collect(Collectors.groupingBy(RulesTreeNode.Rule::languageKey));

    RulesTreeNode rootNode = (RulesTreeNode) model.getRoot();
    rootNode.removeAllChildren();

    for (Map.Entry<String, List<RulesTreeNode.Rule>> e : rulesByLanguage.entrySet()) {
      RulesTreeNode.Language languageNode = new RulesTreeNode.Language(languagesNameByKey.get(e.getKey()));
      for (RulesTreeNode.Rule r : e.getValue()) {
        languageNode.add(r);
      }
      model.refreshLanguageActivation(languageNode);
      rootNode.add(languageNode);
    }

    TreeUtil.sort(rootNode, Comparator.comparing(Object::toString));
    model.reload();
    if (!filterModel.isEmpty()) {
      TreeUtil.expandAll(table.getTree());
    }
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

  private void setDescription(@Nullable RulesTreeNode.Rule rule) {
    String html;

    if (rule == null) {
      html = "Select a rule to see the description";
    } else {
      String attributes = rule.severity() + " " + rule.type();
      attributes = attributes.toLowerCase(Locale.US).replace('_', ' ');
      html = "<b>" + rule.getKey() + "</b> | " + attributes + "<br/>" + rule.getHtmlDescription();
    }
    try {
      descriptionBrowser.read(new StringReader(html), null);
    } catch (IOException e) {
      // ignore
    }
  }

  private ActionToolbar createTreeToolbarPanel() {
    DefaultActionGroup actions = new DefaultActionGroup();

    actions.add(new RulesFilterAction(filterModel));
    actions.addSeparator();

    CommonActionsManager actionManager = CommonActionsManager.getInstance();
    actions.add(actionManager.createExpandAllAction(myTreeExpander, table));
    actions.add(actionManager.createCollapseAllAction(myTreeExpander, table));
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
    actionToolbar.setTargetComponent(panel);
    return actionToolbar;
  }

  private void createUIComponents() {
    panel = new JPanel(new GridBagLayout());

    // create tree table
    model = new RulesTreeTableModel(new RulesTreeNode.Root());
    table = new RulesTreeTable(model);
    table.setTreeCellRenderer(new RulesTreeTableRenderer(filterModel::getText));
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
          setDescription(r);
          return;
        }
      }
      setDescription(null);
    });
    JBScrollPane scrollPane = new JBScrollPane(table);
    table.getTree().setShowsRootHandles(true);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM + SideBorder.LEFT + SideBorder.TOP));

    GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1, 0,
      GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL, JBUI.insets(5, 5, 5, 5), 0, 0);
    JBLabel label = new JBLabel("<html><b>Note: </b>When a project is connected to a SonarQube server or SonarCloud, configuration from the server applies.</html>");
    panel.add(label, gbc);

    // filters
    myTreeExpander = new DefaultTreeExpander(table.getTree()) {
      @Override
      public boolean canExpand() {
        return table.isShowing();
      }

      @Override
      public boolean canCollapse() {
        return table.isShowing();
      }
    };

    filterComponent = new FilterComponent("sonarlint_rule_filter", 10) {
      @Override public void filter() {
        filterModel.setText(getFilter());
      }
    };

    JButton restoreDefaults = new JButton("Restore defaults");
    restoreDefaults.addActionListener(l -> model.restoreDefaults());

    JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
    optionsPanel.add(filterComponent);
    optionsPanel.add(createTreeToolbarPanel().getComponent());
    optionsPanel.add(restoreDefaults, gbc);

    gbc = new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0,
      GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL, JBUI.insets(5, 0, 2, 10), 0, 0);
    panel.add(optionsPanel, gbc);

    // description pane
    descriptionBrowser = new JEditorPane(UIUtil.HTML_MIME, "<html><body></body></html>");
    descriptionBrowser.setEditable(false);
    descriptionBrowser.setBorder(JBUI.Borders.empty(5));
    descriptionBrowser.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    setDescription(null);

    JPanel descriptionPanel = new JPanel(new BorderLayout());
    descriptionPanel.setBorder(IdeBorderFactory.createTitledBorder("Rule description", false,
      new JBInsets(2, 2, 0, 0)));
    descriptionPanel.add(ScrollPaneFactory.createScrollPane(descriptionBrowser), BorderLayout.CENTER);

    JBSplitter mainSplitter = new JBSplitter(false, SPLITTER_KEY, 0.67f);
    mainSplitter.setFirstComponent(scrollPane);
    mainSplitter.setSecondComponent(descriptionPanel);

    gbc = new GridBagConstraints(0, 2, 1, 1, 1.0, 1.0,
      GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insets(5, 0, 2, 10), 0, 0);
    panel.add(mainSplitter, gbc);
  }
}
