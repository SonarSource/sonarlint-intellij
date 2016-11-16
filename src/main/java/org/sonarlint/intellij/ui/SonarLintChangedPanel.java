package org.sonarlint.intellij.ui;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.*;
import java.util.Date;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.scope.AbstractScope;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.TreeModelBuilder;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintChangedPanel extends SimpleToolWindowPanel implements OccurenceNavigator, DataProvider {
  private static final String ID = "SonarLint";
  private static final String GROUP_ID = "SonarLint.changedtoolwindow";
  private static final String SPLIT_PROPORTION = "SONARLINT_CHANGED_ISSUES_SPLIT_PROPORTION";
  private static final String INITIAL_LABEL = "Trigger the analysis to find issues on the files in the change set";

  private final Project project;
  private Tree tree;
  private ActionToolbar mainToolbar;
  private TreeModelBuilder treeBuilder;
  private SonarLintRulePanel rulePanel;
  private JLabel lastAnalysisLabel;
  private Date lastAnalysisTime = null;

  public SonarLintChangedPanel(Project project) {
    super(false, true);
    this.project = project;

    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);

    addToolbar();

    JPanel issuesPanel = new JPanel(new BorderLayout());
    createTree();
    issuesPanel.add(createLastAnalysisPanel(), BorderLayout.NORTH);
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);

    rulePanel = new SonarLintRulePanel(project, projectBindingManager);

    JScrollPane scrollableRulePanel = ScrollPaneFactory.createScrollPane(
      rulePanel.getPanel(),
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollableRulePanel.getVerticalScrollBar().setUnitIncrement(10);

    super.setContent(createSplitter(issuesPanel, scrollableRulePanel));
  }

  private Component createLastAnalysisPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    lastAnalysisLabel = new JLabel(INITIAL_LABEL);
    final GridBagConstraints gc =
      new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
        new Insets(2, 2, 2, 2), 0, 0);
    panel.add(lastAnalysisLabel, gc);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    panel.add(Box.createHorizontalBox(), gc);

    return panel;
  }

  private JComponent createSplitter(JComponent c1, JComponent c2) {
    float savedProportion = PropertiesComponent.getInstance(project).getFloat(SPLIT_PROPORTION, 0.65f);

    final Splitter splitter = new Splitter(false);
    splitter.setFirstComponent(c1);
    splitter.setSecondComponent(c2);
    splitter.setProportion(savedProportion);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.addPropertyChangeListener(Splitter.PROP_PROPORTION,
      evt -> PropertiesComponent.getInstance(project).setValue(SPLIT_PROPORTION, Float.toString(splitter.getProportion())));

    return splitter;
  }

  private void addToolbar() {
    ActionGroup mainActionGroup = (ActionGroup) ActionManager.getInstance().getAction(GROUP_ID);
    mainToolbar = ActionManager.getInstance().createActionToolbar(ID, mainActionGroup, false);
    mainToolbar.setTargetComponent(this);
    Box toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());

    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
  }

  private void expandTree() {
    if (tree.getRowCount() < 30) {
      TreeUtil.expandAll(tree);
    } else {
      tree.expandRow(0);
      if (tree.getRowCount() > 1) {
        tree.expandRow(1);
      }
    }
  }

  private void issueTreeSelectionChanged() {
    IssueNode[] selectedNodes = tree.getSelectedNodes(IssueNode.class, null);
    if (selectedNodes.length > 0) {
      rulePanel.setRuleKey(selectedNodes[0].issue());
    } else {
      rulePanel.setRuleKey(null);
    }
  }

  private void createTree() {
    treeBuilder = new TreeModelBuilder();
    DefaultTreeModel model = treeBuilder.createModel();
    tree = new IssueTree(project, model);
    tree.addTreeSelectionListener(e -> issueTreeSelectionChanged());
  }

  @CheckForNull
  private OccurenceInfo occurrence(@Nullable IssueNode node) {
    if (node == null) {
      return null;
    }

    TreePath path = new TreePath(node.getPath());
    tree.getSelectionModel().setSelectionPath(path);
    tree.scrollPathToVisible(path);

    RangeMarker range = node.issue().getRange();
    int startOffset = (range != null) ? range.getStartOffset() : 0;
    return new OccurenceInfo(
      new OpenFileDescriptor(project, node.issue().psiFile().getVirtualFile(), startOffset),
      -1,
      -1);
  }

  @Override public boolean hasNextOccurence() {
    // relies on the assumption that a TreeNodes will always be the last row in the table view of the tree
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return false;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    if (node instanceof IssueNode) {
      return tree.getRowCount() != tree.getRowForPath(path) + 1;
    } else {
      return node.getChildCount() > 0;
    }
  }

  @Override public boolean hasPreviousOccurence() {
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return false;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    return (node instanceof IssueNode) && !isFirst(node);
  }

  private static boolean isFirst(final TreeNode node) {
    final TreeNode parent = node.getParent();
    return parent == null || (parent.getIndex(node) == 0 && isFirst(parent));
  }

  @CheckForNull
  @Override
  public OccurenceInfo goNextOccurence() {
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    return occurrence(treeBuilder.getNextIssue((AbstractNode<?>) path.getLastPathComponent()));
  }

  @CheckForNull
  @Override
  public OccurenceInfo goPreviousOccurence() {
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    return occurrence(treeBuilder.getPreviousIssue((AbstractNode<?>) path.getLastPathComponent()));
  }

  @Override public String getNextOccurenceActionName() {
    return "Next Issue";
  }

  @Override public String getPreviousOccurenceActionName() {
    return "Previous Issue";
  }
}
