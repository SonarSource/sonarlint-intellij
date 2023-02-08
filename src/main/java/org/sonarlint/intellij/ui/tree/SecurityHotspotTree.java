/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.ide.DefaultTreeExpander;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.actions.OpenSecurityHotspotInBrowserAction;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.nodes.FileNode;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;

/**
 * Extends {@link Tree} to provide context data for actions and initialize it
 */
public class SecurityHotspotTree extends Tree implements DataProvider {
  private final Project project;

  public SecurityHotspotTree(Project project, TreeModel model) {
    super(model);
    this.project = project;
    init();
  }

  private void init() {
    this.setShowsRootHandles(false);
    this.setCellRenderer(new TreeCellRenderer());
    this.expandRow(0);

    var group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(new OpenSecurityHotspotInBrowserAction());
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_ALL));

    PopupHandler.installPopupMenu(this, group, ActionPlaces.TODO_VIEW_POPUP);

    EditSourceOnDoubleClickHandler.install(this);
    EditSourceOnEnterKeyHandler.install(this);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    // use string literal as the key appeared in newer versions
    if ("bgtDataProvider".equals(dataId)) {
      return getBackgroundDataProvider();
    } else if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return navigate();
    } else if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      return new DefaultTreeExpander(this);
    } else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return getSelectedFile();
    } else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      var f = getSelectedFile();
      // return empty so that it doesn't find it in parent components
      return f != null && f.isValid() ? (new VirtualFile[] {f}) : new VirtualFile[0];
    } else if (OpenSecurityHotspotInBrowserAction.Companion.getSECURITY_HOTSPOT_DATA_KEY().is(dataId)) {
      return getSelectedSecurityHotspot();
    }

    return null;
  }

  private DataProvider getBackgroundDataProvider() {
    var file = getSelectedFile();
    if (file != null && file.isValid()) {
      return otherId -> {
        if (CommonDataKeys.PSI_FILE.is(otherId)) {
          return PsiManager.getInstance(project).findFile(file);
        }
        return null;
      };
    }
    return null;
  }

  @CheckForNull
  private OpenFileDescriptor navigate() {
    var securityHotspot = getSelectedSecurityHotspot();
    if (securityHotspot == null || !securityHotspot.isValid()) {
      return null;
    }

    int offset;
    var range = securityHotspot.getRange();
    if (range != null) {
      offset = range.getStartOffset();
    } else {
      offset = 0;
    }
    return new OpenFileDescriptor(project, securityHotspot.psiFile().getVirtualFile(), offset);
  }

  @CheckForNull
  private LiveSecurityHotspot getSelectedSecurityHotspot() {
    var node = getSelectedNode();
    if (!(node instanceof LiveSecurityHotspotNode)) {
      return null;
    }
    return ((LiveSecurityHotspotNode) node).getHotspot();
  }

  @CheckForNull
  private VirtualFile getSelectedFile() {
    var node = getSelectedNode();
    if (!(node instanceof FileNode)) {
      return null;
    }
    var fileNode = (FileNode) node;
    return fileNode.file();
  }

  @CheckForNull
  private DefaultMutableTreeNode getSelectedNode() {
    var path = getSelectionPath();
    if (path == null) {
      return null;
    }
    return (DefaultMutableTreeNode) path.getLastPathComponent();
  }
}
