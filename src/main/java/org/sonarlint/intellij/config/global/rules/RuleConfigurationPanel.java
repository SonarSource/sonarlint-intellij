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
package org.sonarlint.intellij.config.global.rules;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ModalityUiUtil;
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
import java.util.ArrayDeque;
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
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.JTextComponent;
import javax.swing.text.NumberFormatter;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.ConfigurationPanel;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.documentation.SonarLintDocumentation;
import org.sonarlint.intellij.ui.ruledescription.RuleDescriptionPanel;
import org.sonarlint.intellij.ui.ruledescription.RuleHeaderPanel;
import org.sonarlint.intellij.ui.ruledescription.RuleLanguages;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleParamDefinitionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.telemetry.LinkTelemetry.RULE_SELECTION_PAGE;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

public class RuleConfigurationPanel implements Disposable, ConfigurationPanel<SonarLintGlobalSettings> {
  private static final String MAIN_SPLITTER_KEY = "sonarlint_rule_configuration_splitter";
  private static final String RIGHT_SPLITTER_KEY = "sonarlint_rule_configuration_splitter_right";
  private static final float DIVIDER_PROPORTION_DEFAULT = 0.5f;
  // Default proportion is: 85% on top for rule description + 15% on the bottom for parameters
  private static final float DIVIDER_PROPORTION_RULE_DEFAULT = 0.85f;
  @NonNls
  private static final String EMPTY_HTML = "Select a rule to see the description";
  private final Map<String, RulesTreeNode.Rule> allRulesStateByKey = new ConcurrentHashMap<>();
  private final Map<String, RulesTreeNode.LanguageNode> languageNodesByName = new HashMap<>();
  private final RulesFilterModel filterModel = new RulesFilterModel(this::updateModel);
  private final AtomicBoolean isDirty = new AtomicBoolean(false);
  private final Project project = ProjectManager.getInstance().getDefaultProject();
  private RulesTreeTable table;
  private RuleDescriptionPanel ruleDescription;
  private JBLoadingPanel panel;
  private JPanel myParamsPanel;
  private RulesTreeTableModel model;
  private FilterComponent myRuleFilter;
  private TreeExpander myTreeExpander;
  private RulesParamsSeparator rulesParamsSeparator;
  private String selectedRuleKey;
  private RuleHeaderPanel ruleHeaderPanel;

  public RuleConfigurationPanel() {
    createUIComponents();
  }

  private static boolean loadRuleActivation(SonarLintGlobalSettings settings, RuleDefinitionDto ruleDetails) {
    final var ruleInSettings = settings.getRulesByKey().get(ruleDetails.getKey());
    if (ruleInSettings != null) {
      return ruleInSettings.isActive();
    }
    return ruleDetails.isActiveByDefault();
  }

  private static Map<String, String> loadNonDefaultRuleParams(SonarLintGlobalSettings settings, RuleDefinitionDto ruleDetails) {
    var ruleInSettings = settings.getRulesByKey().get(ruleDetails.getKey());
    if (ruleInSettings != null) {
      return ruleInSettings.getParams();
    }
    return Collections.emptyMap();
  }

  private static void addParamLabel(JPanel panel, GridBagConstraints constraints, RuleParamDefinitionDto param) {
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    constraints.weightx = 0.0;
    panel.add(new JBLabel(param.getName()), constraints);
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

  public static List<RulesTreeNode.Rule> getRulesNodes(@Nullable final TreePath[] paths) {
    if (paths == null) {
      return Collections.emptyList();
    }
    final var q = new ArrayDeque<RulesTreeNode>(paths.length);
    for (final TreePath path : paths) {
      if (path != null) {
        q.addLast((RulesTreeNode) path.getLastPathComponent());
      }
    }
    return getRulesNodes(q);
  }

  private static List<RulesTreeNode.Rule> getRulesNodes(final ArrayDeque<RulesTreeNode> queue) {
    final var nodes = new ArrayList<RulesTreeNode.Rule>();
    while (!queue.isEmpty()) {
      final var node = queue.pollFirst();
      if (node instanceof RulesTreeNode.LanguageNode) {
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
    getService(BackendService.class).getListAllStandaloneRulesDefinitions()
      .thenAcceptAsync(response -> {
        var persistedRules = response.getRulesByKey().values().stream()
          .map(ruleDefinitionDto -> new RulesTreeNode.Rule(ruleDefinitionDto,
            loadRuleActivation(getGlobalSettings(), ruleDefinitionDto),
            loadNonDefaultRuleParams(getGlobalSettings(), ruleDefinitionDto)))
          .collect(Collectors.toMap(RulesTreeNode.Rule::getKey, r -> r));

        for (var persisted : persistedRules.values()) {
          final var possiblyModified = allRulesStateByKey.get(persisted.getKey());
          if (!persisted.equals(possiblyModified)) {
            this.isDirty.lazySet(true);
            return;
          }
        }
        this.isDirty.lazySet(false);
      })
      .exceptionally(error -> {
        GlobalLogOutput.get().log("Could not recompute rules: " + error.getMessage(), ClientLogOutput.Level.ERROR);
        return null;
      });
  }

  @Override
  public void save(SonarLintGlobalSettings settings) {
    var nonDefaultRulesConfigurationByKey = allRulesStateByKey.entrySet()
      .stream().filter(e -> e.getValue().isNonDefault())
      .collect(Collectors.toMap(Map.Entry::getKey, e -> {
        var ruleNode = e.getValue();
        var rule = new SonarLintGlobalSettings.Rule(ruleNode.getKey(), ruleNode.isActivated());
        rule.setParams(ruleNode.getCustomParams());
        return rule;
      }));
    settings.setRulesByKey(nonDefaultRulesConfigurationByKey);
    getService(BackendService.class).updateStandaloneRulesConfiguration(nonDefaultRulesConfigurationByKey);
  }

  @Override
  public void load(SonarLintGlobalSettings settings) {
    panel.startLoading();
    selectedRuleKey = null;
    getService(BackendService.class).getListAllStandaloneRulesDefinitions()
      .thenAcceptAsync(response -> {
        allRulesStateByKey.clear();

        var ruleNodes = response.getRulesByKey().values().stream()
          .map(ruleDefinitionDto -> new RulesTreeNode.Rule(ruleDefinitionDto,
            loadRuleActivation(settings, ruleDefinitionDto),
            loadNonDefaultRuleParams(settings, ruleDefinitionDto)))
          .collect(Collectors.toMap(RulesTreeNode.Rule::getKey, r -> r));

        allRulesStateByKey.putAll(ruleNodes);

        ModalityUiUtil.invokeLaterIfNeeded(
          ModalityState.stateForComponent(panel), () -> {
            applyRuleSelection();
            updateModel();
            panel.stopLoading();
          });
      })
      .exceptionally(error -> {
        GlobalLogOutput.get().log("Could not load rules: " + error.getMessage(), ClientLogOutput.Level.ERROR);
        return null;
      });
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
      var languageNode = getOrCreateLanguageNode(entry.getKey());
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

  private RulesTreeNode.@NotNull LanguageNode getOrCreateLanguageNode(Language language) {
    var languageLabel = org.sonarsource.sonarlint.core.client.utils.Language.fromDto(language).getLabel();
    return languageNodesByName.computeIfAbsent(languageLabel, RulesTreeNode.LanguageNode::new);
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
    var rulePanel = new JBPanel<>(new BorderLayout());
    rulePanel.setBorder(JBUI.Borders.emptyLeft(5));

    ruleHeaderPanel = new RuleHeaderPanel(this);
    rulePanel.add(ruleHeaderPanel, BorderLayout.NORTH);

    ruleDescription = new RuleDescriptionPanel(project, this);
    ruleDescription.setBorder(IdeBorderFactory.createBorder());
    rulePanel.add(ruleDescription, BorderLayout.CENTER);

    var rightSplitter = new JBSplitter(true, RIGHT_SPLITTER_KEY, DIVIDER_PROPORTION_RULE_DEFAULT);
    rightSplitter.setFirstComponent(rulePanel);

    myParamsPanel = new JBPanel<>(new GridBagLayout());
    myParamsPanel.setBorder(JBUI.Borders.emptyLeft(12));
    initOptionsAndDescriptionPanel();
    rightSplitter.setSecondComponent(myParamsPanel);
    rightSplitter.setHonorComponentsMinimumSize(true);

    final var tree = initTreeScrollPane();

    final var northPanel = new JBPanel<>(new GridBagLayout());
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

    final var inspectionTreePanel = new JBPanel<>(new BorderLayout());
    inspectionTreePanel.add(northPanel, BorderLayout.NORTH);
    inspectionTreePanel.add(mainSplitter, BorderLayout.CENTER);

    panel = new JBLoadingPanel(new BorderLayout(), this);
    panel.add(inspectionTreePanel, BorderLayout.CENTER);

    var introLabel = new JEditorPane();
    initHtmlPane(introLabel);
    SwingHelper.setHtml(introLabel, "Configure rules used for Sonarlint analysis for projects not in Connected Mode.",
      UIUtil.getLabelForeground());
    var configureRuleLabel = new JEditorPane();
    initHtmlPane(configureRuleLabel);
    SwingHelper.setHtml(configureRuleLabel, "Connecting your project to SonarQube or SonarCloud syncs SonarLint with the " +
      "Quality Profile standards defined on the server, allowing you to share the same rules configuration with your team.",
      JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    var ruleServerLabel = new JEditorPane();
    initHtmlPane(ruleServerLabel);
    ruleServerLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        RULE_SELECTION_PAGE.browseWithTelemetry();
      }
    });
    SwingHelper.setHtml(ruleServerLabel, "<icon src=\"AllIcons.General.BalloonWarning\"> &nbsp;When a project is connected to SonarQube or " +
      "SonarCloud, <a href=\"" + SonarLintDocumentation.Intellij.RULE_SECTION_LINK + "\">configuration from the server applies</a>.", JBUI.CurrentTheme.ContextHelp.FOREGROUND);
    var labelPanel = new JBPanel<>(new VerticalFlowLayout(0, 0));
    labelPanel.add(introLabel);
    labelPanel.add(configureRuleLabel);
    labelPanel.add(ruleServerLabel);
    panel.add(labelPanel, BorderLayout.NORTH);
    return panel;
  }

  private void initOptionsAndDescriptionPanel() {
    myParamsPanel.removeAll();
    ruleDescription.removeAll();
    ruleHeaderPanel.showMessage(EMPTY_HTML);
    myParamsPanel.validate();
    myParamsPanel.repaint();
  }

  private JScrollPane initTreeScrollPane() {
    // create tree table
    model = new RulesTreeTableModel(new RulesTreeNode.Root());
    table = new RulesTreeTable(model);
    table.getModel().addTableModelListener(e -> recomputeDirtyState());
    table.setTreeCellRenderer(new RulesTreeTableRenderer(filterModel::getText));
    table.setRootVisible(false);
    TreeUtil.installActions(table.getTree());
    TreeUIHelper.getInstance().installTreeSpeedSearch(table.getTree(), treePath -> treePath.getLastPathComponent().toString(), false);

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
        ruleHeaderPanel.showMessage("Multiple rules are selected.");
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
    ruleHeaderPanel.updateForRuleConfiguration(singleNode.getKey(), singleNode.type(), singleNode.severity(), singleNode.attribute(), singleNode.impacts());
    var fileType = RuleLanguages.Companion.findFileTypeByRuleLanguage(singleNode.language());

    getService(BackendService.class).getStandaloneRuleDetails(new GetStandaloneRuleDescriptionParams(singleNode.getKey()))
      .thenAcceptAsync(details -> runOnUiThread(project, ModalityState.stateForComponent(getComponent()), () -> {
        details.getDescription().map(
          monolithDescription -> {
            ruleDescription.addMonolith(monolithDescription, fileType);
            return null;
          },
          withSections -> {
            ruleDescription.addSections(withSections, fileType);
            return null;
          });

        myParamsPanel.removeAll();
        final var configPanelAnchor = new JBPanel<>(new GridLayout());
        setConfigPanel(configPanelAnchor, singleNode, details.getRuleDefinition().getParamsByKey());
        if (configPanelAnchor.getComponentCount() != 0) {
          rulesParamsSeparator = new RulesParamsSeparator();
          myParamsPanel.add(rulesParamsSeparator,
            new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
              JBUI.emptyInsets(), 0, 0));
          myParamsPanel.add(configPanelAnchor, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
            JBUI.insetsLeft(2), 0, 0));
        } else {
          myParamsPanel.add(configPanelAnchor, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
            JBUI.insetsLeft(2), 0, 0));
        }
        myParamsPanel.revalidate();
        myParamsPanel.repaint();
      }))
      .exceptionally(error -> {
        GlobalLogOutput.get().log("Could not retrieve rule description", ClientLogOutput.Level.ERROR);
        return null;
      });
  }

  private void setConfigPanel(final JPanel configPanelAnchor, RulesTreeNode.Rule rule, Map<String, RuleParamDefinitionDto> paramsByKey) {
    configPanelAnchor.removeAll();
    final var additionalConfigPanel = ConfigPanelState.of(getAdditionalConfigPanel(rule, paramsByKey)).getPanel(rule.activated);
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
    selectedRuleKey = ruleKey;
    applyRuleSelection();
  }

  private void applyRuleSelection() {
    // rule state loading happens on background thread, might not be ready to apply selection yet
    // should be triggered after rules are loaded
    if (selectedRuleKey != null && allRulesStateByKey.containsKey(selectedRuleKey)) {
      myRuleFilter.setFilter(selectedRuleKey);
      var node = allRulesStateByKey.get(selectedRuleKey);
      var path = new TreePath(node.getPath());
      table.getTree().setSelectionPath(path);
    } else {
      filterModel.reset(false);
      myRuleFilter.reset();
    }
  }

  @CheckForNull
  private JComponent getAdditionalConfigPanel(RulesTreeNode.Rule rule, Map<String, RuleParamDefinitionDto> paramsByKey) {
    if (paramsByKey.isEmpty()) {
      return null;
    }
    final var configPanel = new JBPanel<>(new GridBagLayout());

    final var constraints = new GridBagConstraints();
    constraints.gridy = 0;
    for (var param : paramsByKey.values()) {
      constraints.gridx = 0;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.anchor = GridBagConstraints.BASELINE_LEADING;
      constraints.gridwidth = 1;
      switch (param.getType()) {
        case TEXT -> createTextParam(rule, configPanel, constraints, param);
        case STRING -> createStringParam(rule, configPanel, constraints, param);
        case INTEGER -> createIntParam(rule, configPanel, constraints, param);
        case FLOAT -> createFloatParam(rule, configPanel, constraints, param);
        case BOOLEAN -> createBooleanParam(rule, configPanel, constraints, param);
        default -> GlobalLogOutput.get().log("Unknown rule parameter type: " + param.getType() + " for rule " + rule.getKey(), ClientLogOutput.Level.ERROR);
      }
      constraints.gridy++;
    }
    // Add an empty panel to fill the space
    constraints.weighty = 1.0;
    configPanel.add(new JBPanel<>(), constraints);
    return configPanel;
  }

  private void createTextParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RuleParamDefinitionDto param) {
    addParamLabel(panel, constraints, param);
    addTextField(rule, panel, constraints, param, new JBTextArea());
  }

  private void createStringParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RuleParamDefinitionDto param) {
    addParamLabel(panel, constraints, param);
    addTextField(rule, panel, constraints, param, new ExpandableTextField(s -> StringUtil.split(s, ","), l -> StringUtil.join(l, ",")));
  }

  private void createIntParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RuleParamDefinitionDto param) {
    addParamLabel(panel, constraints, param);
    // See NumberFormat.parse(), it will return a Long...
    var longFormat = NumberFormat.getIntegerInstance();
    addNumberFormattedTextField(rule, panel, constraints, param, longFormat, asLong(param.getDefaultValue()),
      asLong(rule.getCustomParams().getOrDefault(param.getKey(), param.getDefaultValue())));
  }

  private void createFloatParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RuleParamDefinitionDto param) {
    addParamLabel(panel, constraints, param);
    var floatFormat = NumberFormat.getNumberInstance();
    addNumberFormattedTextField(rule, panel, constraints, param, floatFormat, asDouble(param.getDefaultValue()),
      asDouble(rule.getCustomParams().getOrDefault(param.getKey(), param.getDefaultValue())));
  }

  private void createBooleanParam(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RuleParamDefinitionDto param) {
    final var checkBox = new JBCheckBox(param.getName(), asBoolean(rule.getCustomParams().getOrDefault(param.getKey(), param.getDefaultValue())));
    checkBox.setToolTipText(param.getDescription());
    checkBox.addActionListener(e -> {
      final Boolean b = checkBox.isSelected();
      if (!b.equals(asBoolean(param.getDefaultValue()))) {
        rule.getCustomParams().put(param.getKey(), b.toString());
      } else {
        rule.getCustomParams().remove(param.getKey());
      }
      recomputeDirtyState();
      rulesParamsSeparator.updateDefaultLinkVisibility();
    });
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    panel.add(checkBox, constraints);
  }

  private void addTextField(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RuleParamDefinitionDto param, JTextComponent textComponent) {
    textComponent.setToolTipText(param.getDescription());
    textComponent.setText(rule.getCustomParams().getOrDefault(param.getKey(), param.getDefaultValue()));
    textComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent e) {
        var value = textComponent.getText();
        if (!value.equals(param.getDefaultValue())) {
          rule.getCustomParams().put(param.getKey(), value);
        } else {
          rule.getCustomParams().remove(param.getKey());
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

  private void addNumberFormattedTextField(RulesTreeNode.Rule rule, JPanel panel, GridBagConstraints constraints, RuleParamDefinitionDto param, NumberFormat numberFormat,
    Number defaultValue, Number initialValue) {
    final var valueField = new JFormattedTextField();
    valueField.setToolTipText(param.getDescription());
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
            rule.getCustomParams().put(param.getKey(), String.valueOf(value));
          } else {
            rule.getCustomParams().remove(param.getKey());
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

  private RulesTreeNode.Rule getStrictlySelectedToolNode() {
    var paths = table.getTree().getSelectionPaths();
    return paths != null && paths.length == 1 && paths[0].getLastPathComponent() instanceof RulesTreeNode.Rule rule
      ? rule
      : null;
  }

  public Collection<RulesTreeNode.Rule> getSelectedRuleNodes() {
    return getRulesNodes(table.getTree().getSelectionPaths());
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
        var q = new ArrayDeque<Component>(1);
        q.addLast(optionsPanel);
        while (!q.isEmpty()) {
          final var current = q.pollFirst();
          current.addPropertyChangeListener("enabled", getChangeListener(current));
          if (current.isEnabled()) {
            myEnableRequiredComponent.add(current);
          }
          if (current instanceof Container container) {
            for (var child : container.getComponents()) {
              q.addLast(child);
            }
          }
        }
      }
    }

    private static ConfigPanelState of(@Nullable JComponent panel) {
      return panel == null ? EMPTY : new ConfigPanelState(panel);
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
  }

  private class RulesParamsSeparator extends JPanel {
    private final ActionLink myDefaultsLink;

    RulesParamsSeparator() {
      setLayout(new GridBagLayout());
      var optionsLabelConstraints = new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsLeft(2), 0, 0);
      add(new JBLabel("Options"), optionsLabelConstraints);
      var separatorConstraints = new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, JBUI.insets(2,
        TitledSeparator.SEPARATOR_LEFT_INSET,
        0,
        TitledSeparator.SEPARATOR_RIGHT_INSET),
        0, 0);
      add(new JSeparator(SwingConstants.HORIZONTAL), separatorConstraints);
      var defaultLabelConstraints = new GridBagConstraints(2, 0, 0, 1, 0, 1, GridBagConstraints.EAST, GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0);

      myDefaultsLink = new ActionLink("Defaults", event -> {
        var node = getStrictlySelectedToolNode();
        if (node != null) {
          node.getCustomParams().clear();
          updateParamsAndDescriptionPanel(node);
          model.nodeChanged(node);
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
