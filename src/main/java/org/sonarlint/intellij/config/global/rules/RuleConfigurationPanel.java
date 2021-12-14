/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.Queue;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.JTextComponent;
import javax.swing.text.NumberFormatter;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.core.SonarLintEngineManager;
import org.sonarlint.intellij.ui.ruledescription.RuleDescriptionHTMLEditorKit;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.core.RuleDescription.appendRuleAttributesHtmlTable;

public class RuleConfigurationPanel implements Disposable, ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String MAIN_SPLITTER_KEY = "sonarlint_rule_configuration_splitter";
  private static final String RIGHT_SPLITTER_KEY = "sonarlint_rule_configuration_splitter_right";
  private static final float DIVIDER_PROPORTION_DEFAULT = 0.5f;
  // Default proportion is: 85% on top for rule description + 15% on the bottom for parameters
  private static final float DIVIDER_PROPORTION_RULE_DEFAULT = 0.85f;
  @NonNls
  private static final String EMPTY_HTML = "Select a rule to see the description";

  private final RulesFilterModel filterModel = new RulesFilterModel(this::updateModel);
  private RulesTreeTable table;
  private JEditorPane descriptionBrowser;
  private JBLoadingPanel panel;
  private JPanel myParamsPanel;
  private RulesTreeTableModel model;
  private FilterComponent myRuleFilter;
  private TreeExpander myTreeExpander;
  private RulesParamsSeparator rulesParamsSeparator;
  private final Map<String, RulesTreeNode.Rule> allRulesStateByKey = new ConcurrentHashMap<>();
  private final Map<String, RulesTreeNode.Language> languageNodesByName = new HashMap<>();
  private final AtomicBoolean isDirty = new AtomicBoolean(false);

  public RuleConfigurationPanel() {
    createUIComponents();
  }

  @Override
  public void dispose() {
    // Only used as a parent disposable
  }

  @Override
  public JComponent getComponent() {
    return panel;
  }

  @Override
  public boolean isModified(SonarLintGlobalSettings settings) {
    return isDirty.get();
  }

  private void recomputeDirtyState() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      var persistedRules = loadRuleNodes(getGlobalSettings());
      for (var persisted : persistedRules.values()) {
        final var possiblyModified = allRulesStateByKey.get(persisted.getKey());
        if (!persisted.equals(possiblyModified)) {
          this.isDirty.lazySet(true);
          return;
        }
      }
      this.isDirty.lazySet(false);
    });
  }

  @Override
  public void save(SonarLintGlobalSettings settings) {
    allRulesStateByKey.values().forEach(r -> {
      if (r.isNonDefault()) {
        settings.getRulesByKey().computeIfAbsent(r.getKey(), k -> new SonarLintGlobalSettings.Rule(r.getKey(), r.isActivated())).setParams(r.getCustomParams());
      } else {
        settings.getRulesByKey().remove(r.getKey());
      }
    });
  }

  @Override
  public void load(SonarLintGlobalSettings settings) {
    // Loading rules may take time, so do that on a background thread
    panel.startLoading();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      allRulesStateByKey.clear();
      allRulesStateByKey.putAll(loadRuleNodes(settings));

      UIUtil.invokeLaterIfNeeded(() -> {
        filterModel.reset(false);
        myRuleFilter.reset();

        updateModel();
        panel.stopLoading();
      });
    });
  }

  @NotNull
  private static Map<String, RulesTreeNode.Rule> loadRuleNodes(SonarLintGlobalSettings settings) {
    var engine = SonarLintUtils.getService(SonarLintEngineManager.class).getStandaloneEngine();
    return engine.getAllRuleDetails().stream()
      .map(r -> new RulesTreeNode.Rule(r, loadRuleActivation(settings, r), loadNonDefaultRuleParams(settings, r)))
      .collect(Collectors.toMap(RulesTreeNode.Rule::getKey, r -> r));
  }

  private void restoreDefaults() {
    allRulesStateByKey.values().forEach(r -> {
      r.setIsActivated(r.getDefaultActivation());
      r.getCustomParams().clear();
    });
    updateModel();
    recomputeDirtyState();
  }

  private void updateModel() {
    var selectionPaths = table.getTree().getSelectionPaths();
    var rulesByLanguage = allRulesStateByKey.values().stream()
      .filter(filterModel::filter)
      .collect(Collectors.groupingBy(RulesTreeNode.Rule::language));

    var rootNode = (RulesTreeNode) model.getRoot();
    rootNode.removeAllChildren();

    for (var entry : rulesByLanguage.entrySet()) {
      var languageNode = getOrCreateLanguageNode(entry.getKey().getLabel());
      languageNode.removeAllChildren();
      for (var ruleNode : entry.getValue()) {
        languageNode.add(ruleNode);
      }
      model.refreshLanguageActivation(languageNode);
      rootNode.add(languageNode);
    }

    TreeUtil.sort(rootNode, Comparator.comparing(Object::toString));
    model.reload();
    if (!filterModel.isEmpty()) {
      TreeUtil.expandAll(table.getTree());
    }
    table.getTree().setSelectionPaths(selectionPaths);
  }

  @NotNull
  private RulesTreeNode.Language getOrCreateLanguageNode(String languageName) {
    return languageNodesByName.computeIfAbsent(languageName, RulesTreeNode.Language::new);
  }

  private static boolean loadRuleActivation(SonarLintGlobalSettings settings, StandaloneRuleDetails ruleDetails) {
    final var ruleInSettings = settings.getRulesByKey().get(ruleDetails.getKey());
    if (ruleInSettings != null) {
      return ruleInSettings.isActive();
    }
    return ruleDetails.isActiveByDefault();
  }

  private static Map<String, String> loadNonDefaultRuleParams(SonarLintGlobalSettings settings, RuleDetails ruleDetails) {
    var ruleInSettings = settings.getRulesByKey().get(ruleDetails.getKey());
    if (ruleInSettings != null) {
      return ruleInSettings.getParams();
    }
    return Collections.emptyMap();
  }

  private ActionToolbar createTreeToolbarPanel() {
    var actions = new DefaultActionGroup();

    actions.add(new RulesFilterAction(filterModel));
    actions.addSeparator();

    var actionManager = CommonActionsManager.getInstance();
    actions.add(actionManager.createExpandAllAction(myTreeExpander, table));
    actions.add(actionManager.createCollapseAllAction(myTreeExpander, table));
    var actionToolbar = ActionManager.getInstance().createActionToolbar("SonarLintRulesTreeToolbar", actions, true);
    actionToolbar.setTargetComponent(panel);
    return actionToolbar;
  }

  private JPanel createUIComponents() {

    descriptionBrowser = new JEditorPane(UIUtil.HTML_MIME, EMPTY_HTML);
    descriptionBrowser.setEditorKit(new RuleDescriptionHTMLEditorKit());
    descriptionBrowser.setEditable(false);
    descriptionBrowser.setBorder(JBUI.Borders.empty(5));
    descriptionBrowser.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    var descriptionPanel = new JPanel(new BorderLayout());
    descriptionPanel.setBorder(IdeBorderFactory.createTitledBorder("Rule description", false,
      JBUI.insetsLeft(12)).setShowLine(false));
    descriptionPanel.add(ScrollPaneFactory.createScrollPane(descriptionBrowser), BorderLayout.CENTER);

    var rightSplitter = new JBSplitter(true, RIGHT_SPLITTER_KEY, DIVIDER_PROPORTION_RULE_DEFAULT);
    rightSplitter.setFirstComponent(descriptionPanel);

    myParamsPanel = new JPanel(new GridBagLayout());
    myParamsPanel.setBorder(JBUI.Borders.emptyLeft(12));
    initOptionsAndDescriptionPanel();
    rightSplitter.setSecondComponent(myParamsPanel);
    rightSplitter.setHonorComponentsMinimumSize(true);

    final var tree = initTreeScrollPane();

    final var northPanel = new JPanel(new GridBagLayout());
    northPanel.setBorder(JBUI.Borders.empty(2, 0));
    myRuleFilter.setPreferredSize(new Dimension(20, myRuleFilter.getPreferredSize().height));
    northPanel.add(myRuleFilter, new GridBagConstraints(0, 0, 1, 1, 0.5, 1, GridBagConstraints.BASELINE_TRAILING, GridBagConstraints.HORIZONTAL,
      JBUI.emptyInsets(), 0, 0));
    northPanel.add(createTreeToolbarPanel().getComponent(), new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.HORIZONTAL,
      JBUI.emptyInsets(), 0, 0));
    var restoreDefaults = new JButton("Restore Defaults");
    restoreDefaults.setToolTipText("Restore all rules to defaults activation and parameters values");
    restoreDefaults.addActionListener(l -> restoreDefaults());
    northPanel.add(restoreDefaults, new GridBagConstraints(2, 0, 1, 1, 0, 1, GridBagConstraints.BASELINE_LEADING, GridBagConstraints.NONE,
      JBUI.emptyInsets(), 0, 0));

    var mainSplitter = new OnePixelSplitter(false, DIVIDER_PROPORTION_DEFAULT, 0.01f, 0.99f);
    mainSplitter.setSplitterProportionKey(MAIN_SPLITTER_KEY);
    mainSplitter.setFirstComponent(tree);
    mainSplitter.setSecondComponent(rightSplitter);
    mainSplitter.setHonorComponentsMinimumSize(false);

    final var inspectionTreePanel = new JPanel(new BorderLayout());
    inspectionTreePanel.add(northPanel, BorderLayout.NORTH);
    inspectionTreePanel.add(mainSplitter, BorderLayout.CENTER);

    panel = new JBLoadingPanel(new BorderLayout(), this);
    panel.add(inspectionTreePanel, BorderLayout.CENTER);

    var label = new JBLabel("<html><b>Note: </b>When a project is bound to a SonarQube server or SonarCloud, the configuration in this tab is ignored. " +
      "In this case, rules configuration from the server applies, except for rules in the \"Secrets\" category that will always be activated.</html>");
    panel.add(label, BorderLayout.NORTH);
    return panel;
  }

  private void initOptionsAndDescriptionPanel() {
    myParamsPanel.removeAll();
    setHTML(descriptionBrowser, EMPTY_HTML);
    myParamsPanel.validate();
    myParamsPanel.repaint();
  }

  public static void setHTML(JEditorPane browser, String text) {
    SwingHelper.setHtml(browser, text, UIUtil.getLabelForeground());
    browser.setCaretPosition(0);
  }

  private JScrollPane initTreeScrollPane() {
    // create tree table
    model = new RulesTreeTableModel(new RulesTreeNode.Root());
    table = new RulesTreeTable(model);
    table.getModel().addTableModelListener(e -> recomputeDirtyState());
    table.setTreeCellRenderer(new RulesTreeTableRenderer(filterModel::getText));
    table.setRootVisible(false);
    TreeUtil.installActions(table.getTree());
    new TreeSpeedSearch(table.getTree(), treePath -> treePath.getLastPathComponent().toString());

    table.getTree().addTreeSelectionListener(e -> {
      var path = e.getNewLeadSelectionPath();
      if (path != null && path.getLastPathComponent() instanceof RulesTreeNode.Rule) {
        updateParamsAndDescriptionPanel();
      } else {
        initOptionsAndDescriptionPanel();
      }
    });
    var scrollPane = new JBScrollPane(table);
    table.getTree().setShowsRootHandles(true);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM + SideBorder.LEFT + SideBorder.TOP));
    TreeUtil.collapseAll(table.getTree(), 1);

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

    myRuleFilter = new FilterComponent("sonarlint_rule_filter", 10) {
      @Override
      public void filter() {
        filterModel.setText(getFilter());
      }
    };

    return scrollPane;
  }

  private void updateParamsAndDescriptionPanel() {
    var nodes = getSelectedRuleNodes();
    if (!nodes.isEmpty()) {
      final var singleNode = getStrictlySelectedToolNode();
      if (singleNode != null) {
        updateParamsAndDescriptionPanel(singleNode);
      } else {
        setHTML(descriptionBrowser, "Multiple rules are selected.");
        myParamsPanel.removeAll();

      }
    } else {
      initOptionsAndDescriptionPanel();
      myParamsPanel.repaint();
      myParamsPanel.revalidate();
      myParamsPanel.repaint();
    }
  }

  private void updateParamsAndDescriptionPanel(RulesTreeNode.Rule singleNode) {
    var htmlDescription = singleNode.getHtmlDescription();
    var builder = new StringBuilder(htmlDescription.length() + 64);
    appendRuleAttributesHtmlTable(singleNode.getKey(), singleNode.severity(), singleNode.type(), builder);
    builder.append(htmlDescription);
    try {
      setHTML(descriptionBrowser, SearchUtil.markup(builder.toString(), myRuleFilter.getFilter()));
    } catch (Throwable t) {
      GlobalLogOutput.get().logError("Failed to load description for: " + singleNode.getKey() + "; description: " + builder, t);
    }

    myParamsPanel.removeAll();
    final var configPanelAnchor = new JPanel(new GridLayout());
    setConfigPanel(configPanelAnchor, singleNode);
    if (configPanelAnchor.getComponentCount() != 0) {
      rulesParamsSeparator = new RulesParamsSeparator();
      myParamsPanel.add(rulesParamsSeparator,
        new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
          JBUI.emptyInsets(), 0, 0));
      myParamsPanel.add(configPanelAnchor, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
        JBUI.insets(0, 2, 0, 0), 0, 0));
    } else {
      myParamsPanel.add(configPanelAnchor, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
        JBUI.insets(0, 2, 0, 0), 0, 0));
    }
    myParamsPanel.revalidate();
    myParamsPanel.repaint();
  }

  private void setConfigPanel(final JPanel configPanelAnchor, RulesTreeNode.Rule rule) {
    configPanelAnchor.removeAll();
    final var additionalConfigPanel = ConfigPanelState.of(getAdditionalConfigPanel(rule)).getPanel(rule.activated);
    if (additionalConfigPanel != null) {
      // assume that the panel does not need scrolling if it already contains a scrollable content
      if (UIUtil.hasScrollPane(additionalConfigPanel)) {
        configPanelAnchor.add(additionalConfigPanel);
      } else {
        configPanelAnchor.add(ScrollPaneFactory.createScrollPane(additionalConfigPanel, SideBorder.NONE));
      }
    }
  }

  public void selectRule(String ruleKey) {
    myRuleFilter.setFilter(ruleKey);
    var node = allRulesStateByKey.get(ruleKey);
    var path = new TreePath(node.getPath());
    table.getTree().setSelectionPath(path);
  }

  private static class ConfigPanelState {
    private static final ConfigPanelState EMPTY = new ConfigPanelState(null);

    private final JComponent myOptionsPanel;
    private final Set<Component> myEnableRequiredComponent = new HashSet<>();

    private boolean myLastState = true;
    private boolean myDeafListeners;

    private ConfigPanelState(@Nullable JComponent optionsPanel) {
      myOptionsPanel = optionsPanel;
      if (myOptionsPanel != null) {
        var q = new Queue<Component>(1);
        q.addLast(optionsPanel);
        while (!q.isEmpty()) {
          final var current = q.pullFirst();
          current.addPropertyChangeListener("enabled", getChangeListener(current));
          if (current.isEnabled()) {
            myEnableRequiredComponent.add(current);
          }
          if (current instanceof Container) {
            for (var child : ((Container) current).getComponents()) {
              q.addLast(child);
            }
          }
        }
      }
    }

    @NotNull
    private PropertyChangeListener getChangeListener(Component current) {
      return evt -> {
        if (!myDeafListeners) {
          final var newValue = (boolean) evt.getNewValue();
          if (newValue) {
            myEnableRequiredComponent.add(current);
          } else {
            myEnableRequiredComponent.remove(current);
          }
        }
      };
    }

    @CheckForNull
    private JComponent getPanel(boolean currentState) {
      if (myOptionsPanel != null && myLastState != currentState) {
        myDeafListeners = true;
        try {
          for (var c : myEnableRequiredComponent) {
            c.setEnabled(currentState);
          }
          myLastState = currentState;
        } finally {
          myDeafListeners = false;
        }
      }
      return myOptionsPanel;
    }

    private static ConfigPanelState of(@Nullable JComponent panel) {
      return panel == null ? EMPTY : new ConfigPanelState(panel);
    }
  }

  @CheckForNull
  private JComponent getAdditionalConfigPanel(RulesTreeNode.Rule rule) {
    if (!rule.hasParameters()) {
      return null;
    }
    final var configPanel = new JPanel(new GridBagLayout());

    final var constraints = new GridBagConstraints();
    constraints.gridy = 0;
    for (var param : rule.getParamDetails()) {
      constraints.gridx = 0;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.anchor = GridBagConstraints.BASELINE_LEADING;
      constraints.gridwidth = 1;
      switch (param.type) {
        case TEXT:
          createTextParam(rule, configPanel, constraints, param);
          break;
        case STRING:
          createStringParam(rule, configPanel, constraints, param);
          break;
        case INTEGER:
          createIntParam(rule, configPanel, constraints, param);
          break;
        case FLOAT:
          createFloatParam(rule, configPanel, constraints, param);
          break;
        case BOOLEAN:
          createBooleanParam(rule, configPanel, constraints, param);
          break;
        default:
          GlobalLogOutput.get().log("Unknown rule parameter type: " + param.type + " for rule " + rule.getKey(), ClientLogOutput.Level.ERROR);
      }
      constraints.gridy++;
    }
    // Add an empty panel to fill the space
    constraints.weighty = 1.0;
    configPanel.add(new JPanel(), constraints);
    return configPanel;
  }

  private void createTextParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RulesTreeNode.RuleParam param) {
    addParamLabel(panel, constraints, param);
    addTextField(rule, panel, constraints, param, new JBTextArea());
  }

  private void createStringParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RulesTreeNode.RuleParam param) {
    addParamLabel(panel, constraints, param);
    addTextField(rule, panel, constraints, param, new ExpandableTextField(s -> StringUtil.split(s, ","), l -> StringUtil.join(l, ",")));
  }

  private void createIntParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RulesTreeNode.RuleParam param) {
    addParamLabel(panel, constraints, param);
    // See NumberFormat.parse(), it will return a Long...
    var longFormat = NumberFormat.getIntegerInstance();
    addNumberFormattedTextField(rule, panel, constraints, param, longFormat, asLong(param.defaultValue),
      asLong(rule.getCustomParams().getOrDefault(param.key, param.defaultValue)));
  }

  private void createFloatParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RulesTreeNode.RuleParam param) {
    addParamLabel(panel, constraints, param);
    var floatFormat = NumberFormat.getNumberInstance();
    addNumberFormattedTextField(rule, panel, constraints, param, floatFormat, asDouble(param.defaultValue),
      asDouble(rule.getCustomParams().getOrDefault(param.key, param.defaultValue)));
  }

  private void createBooleanParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RulesTreeNode.RuleParam param) {
    final var checkBox = new JBCheckBox(param.name, asBoolean(rule.getCustomParams().getOrDefault(param.key, param.defaultValue)));
    checkBox.setToolTipText(param.description);
    checkBox.addActionListener(e -> {
      final Boolean b = checkBox.isSelected();
      if (!b.equals(asBoolean(param.defaultValue))) {
        rule.getCustomParams().put(param.key, b.toString());
      } else {
        rule.getCustomParams().remove(param.key);
      }
      recomputeDirtyState();
      rulesParamsSeparator.updateDefaultLinkVisibility();
    });
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    panel.add(checkBox, constraints);
  }

  private void addTextField(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RulesTreeNode.RuleParam param, JTextComponent textComponent) {
    textComponent.setToolTipText(param.description);
    textComponent.setText(rule.getCustomParams().getOrDefault(param.key, param.defaultValue));
    textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent e) {
        var value = textComponent.getText();
        if (!value.equals(param.defaultValue)) {
          rule.getCustomParams().put(param.key, value);
        } else {
          rule.getCustomParams().remove(param.key);
        }
        recomputeDirtyState();
        rulesParamsSeparator.updateDefaultLinkVisibility();
      }
    });
    constraints.gridx = 1;
    constraints.weightx = 1.0;
    constraints.insets.right = 0;
    panel.add(textComponent, constraints);
  }

  private void addNumberFormattedTextField(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RulesTreeNode.RuleParam param, NumberFormat numberFormat,
    Number defaultValue, Number initialValue) {
    final var valueField = new JFormattedTextField();
    valueField.setToolTipText(param.description);
    valueField.setColumns(4);
    valueField.setFormatterFactory(new DefaultFormatterFactory(new NumberFormatter(numberFormat)));
    valueField.setValue(initialValue);
    valueField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent e) {
        try {
          valueField.commitEdit();
          var value = (Number) valueField.getValue();
          if (value.doubleValue() != defaultValue.doubleValue()) {
            rule.getCustomParams().put(param.key, String.valueOf(value));
          } else {
            rule.getCustomParams().remove(param.key);
          }
          recomputeDirtyState();
          rulesParamsSeparator.updateDefaultLinkVisibility();
        } catch (ParseException e1) {
          // No luck this time
        }
      }
    });
    constraints.gridx = 1;
    constraints.weightx = 1.0;
    constraints.insets.right = 0;
    panel.add(valueField, constraints);
  }

  private static void addParamLabel(JPanel panel, GridBagConstraints constraints, RulesTreeNode.RuleParam param) {
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    constraints.weightx = 0.0;
    panel.add(new JBLabel(param.name), constraints);
  }

  private static boolean asBoolean(@Nullable String value) {
    return "true".equals(value);
  }

  private static long asLong(@Nullable String value) {
    if (StringUtil.isEmpty(value)) {
      return 0;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static double asDouble(@Nullable String value) {
    if (StringUtil.isEmpty(value)) {
      return 0;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private RulesTreeNode.Rule getStrictlySelectedToolNode() {
    var paths = table.getTree().getSelectionPaths();
    return paths != null && paths.length == 1 && paths[0].getLastPathComponent() instanceof RulesTreeNode.Rule
      ? (RulesTreeNode.Rule) paths[0].getLastPathComponent()
      : null;
  }

  public Collection<RulesTreeNode.Rule> getSelectedRuleNodes() {
    return getRulesNodes(table.getTree().getSelectionPaths());
  }

  public static List<RulesTreeNode.Rule> getRulesNodes(@Nullable final TreePath[] paths) {
    if (paths == null) {
      return Collections.emptyList();
    }
    final var q = new Queue<RulesTreeNode>(paths.length);
    for (final TreePath path : paths) {
      if (path != null) {
        q.addLast((RulesTreeNode) path.getLastPathComponent());
      }
    }
    return getRulesNodes(q);
  }

  private static List<RulesTreeNode.Rule> getRulesNodes(final Queue<RulesTreeNode> queue) {
    final var nodes = new ArrayList<RulesTreeNode.Rule>();
    while (!queue.isEmpty()) {
      final var node = queue.pullFirst();
      if (node instanceof RulesTreeNode.Language) {
        for (var i = 0; i < node.getChildCount(); i++) {
          final var childNode = (RulesTreeNode) node.getChildAt(i);
          queue.addLast(childNode);
        }
      } else {
        nodes.add((RulesTreeNode.Rule) node);
      }
    }
    return new ArrayList<>(nodes);
  }

  private class RulesParamsSeparator extends JPanel {
    private final LinkLabel<?> myDefaultsLink;

    RulesParamsSeparator() {
      setLayout(new GridBagLayout());
      var optionsLabelConstraints = new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(0, 2, 0, 0), 0, 0);
      add(new JBLabel("Options"), optionsLabelConstraints);
      var separatorConstraints = new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, JBUI.insets(2,
        TitledSeparator.SEPARATOR_LEFT_INSET,
        0,
        TitledSeparator.SEPARATOR_RIGHT_INSET),
        0, 0);
      add(new JSeparator(SwingConstants.HORIZONTAL), separatorConstraints);
      var defaultLabelConstraints = new GridBagConstraints(2, 0, 0, 1, 0, 1, GridBagConstraints.EAST, GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0);

      myDefaultsLink = LinkLabel.create("Defaults", () -> {
        var node = getStrictlySelectedToolNode();
        if (node != null) {
          node.getCustomParams().clear();
          updateParamsAndDescriptionPanel(node);
        }
      });
      myDefaultsLink.setToolTipText("Restore current rule parameters to default values");
      add(myDefaultsLink, defaultLabelConstraints);
      recomputeDirtyState();
      updateDefaultLinkVisibility();
    }

    public void updateDefaultLinkVisibility() {
      if (table == null) {
        return;
      }
      var node = getStrictlySelectedToolNode();
      if (node != null) {
        var canReset = !node.getCustomParams().isEmpty();
        myDefaultsLink.setVisible(canReset);
        revalidate();
        repaint();
      }
    }

  }

}
