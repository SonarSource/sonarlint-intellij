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
package org.sonarlint.intellij.ui.tree;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.actions.DisableRuleAction;
import org.sonarlint.intellij.actions.ExcludeFileAction;
import org.sonarlint.intellij.actions.SonarAnalyzeFilesAction;
import org.sonarlint.intellij.actions.MarkAsResolvedAction;
import org.sonarlint.intellij.actions.ReopenIssueAction;
import org.sonarlint.intellij.actions.SuggestCodeFixIntentionAction;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.ui.icons.SonarLintIcons;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.report.FindingSelectionManager;

import static org.sonarlint.intellij.util.DataKeys.ISSUE_DATA_KEY;

/**
 * Extends {@link Tree} to provide context data for actions and initialize it
 */
public class IssueTree extends FindingTree implements DataProvider {
  private final Project project;
  @Nullable
  private final FindingSelectionManager selectionManager;

  public IssueTree(Project project, TreeModel model) {
    this(project, model, null);
  }

  public IssueTree(Project project, TreeModel model, @Nullable FindingSelectionManager selectionManager) {
    super(project, model);
    this.project = project;
    this.selectionManager = selectionManager;
    init();
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    Object data = this.getDataInner(dataId);

    if (data != null) {
      return data;
    } else if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return navigate();
    } else if (ISSUE_DATA_KEY.is(dataId)) {
      return getSelectedIssue();
    }

    return null;
  }

  private void init() {
    this.setShowsRootHandles(false);
    var renderer = new TreeCellRenderer();
    renderer.setSelectionManager(selectionManager);
    this.setCellRenderer(renderer);
    this.expandRow(0);

    if (selectionManager != null) {
      var sm = selectionManager;
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          TreePath path = getPathForLocation(e.getX(), e.getY());
          if (path == null) return;
          Object node = path.getLastPathComponent();
          if (!(node instanceof IssueNode issueNode)) return;
          Rectangle bounds = getPathBounds(path);
          if (bounds == null) return;
          int clickOffset = e.getX() - bounds.x;
          // First ~20px is the checkbox icon area
          if (clickOffset >= 0 && clickOffset <= 20) {
            sm.toggle(issueNode.issue().getId());
            repaint();
            e.consume();
          }
        }
      });
    }

    var group = new DefaultActionGroup();
    group.add(new SuggestCodeFixIntentionAction(getSelectedIssue()));
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(new MarkAsResolvedAction());
    group.add(new ReopenIssueAction());
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_ALL));
    group.addSeparator();
    group.add(new SonarAnalyzeFilesAction("Analyse file", "Run SonarQube for IDE analysis on the selected file", SonarLintIcons.PLAY));
    group.add(new ExcludeFileAction("Exclude file(s) from automatic analysis"));
    group.add(new DisableRuleAction());
    PopupHandler.installPopupMenu(this, group, ActionPlaces.TODO_VIEW_POPUP);

    EditSourceOnDoubleClickHandler.install(this);
    EditSourceOnEnterKeyHandler.install(this);
  }

  @CheckForNull
  private OpenFileDescriptor navigate() {
    var issue = getSelectedIssue();
    if (issue == null || !issue.isValid()) {
      return null;
    }

    int offset;
    var range = issue.getRange();
    if (range != null) {
      offset = range.getStartOffset();
    } else {
      offset = 0;
    }
    return new OpenFileDescriptor(project, issue.file(), offset);
  }

  @CheckForNull
  private LiveIssue getSelectedIssue() {
    var node = getSelectedNode();
    if (!(node instanceof IssueNode)) {
      return null;
    }
    return ((IssueNode) node).issue();
  }
}
