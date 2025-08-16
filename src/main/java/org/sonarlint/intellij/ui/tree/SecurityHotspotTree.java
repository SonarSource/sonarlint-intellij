/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.tree.TreeModel;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.actions.OpenSecurityHotspotInBrowserAction;
import org.sonarlint.intellij.actions.ReviewSecurityHotspotAction;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;

/**
 * Extends {@link Tree} to provide context data for actions and initialize it
 */
public class SecurityHotspotTree extends FindingTree implements DataProvider {
  private final Project project;

  public SecurityHotspotTree(Project project, TreeModel model) {
    super(project, model);
    this.project = project;
    init();
  }

  private void init() {
    this.setShowsRootHandles(false);
    this.setCellRenderer(new TreeCellRenderer());

    var group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(new OpenSecurityHotspotInBrowserAction());
    group.add(new ReviewSecurityHotspotAction());
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_ALL));

    PopupHandler.installPopupMenu(this, group, ActionPlaces.TODO_VIEW_POPUP);

    EditSourceOnDoubleClickHandler.install(this);
    EditSourceOnEnterKeyHandler.install(this);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    Object data = this.getDataInner(dataId);

    if (data != null) {
      return data;
    } else if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return navigate();
    } else if (OpenSecurityHotspotInBrowserAction.Companion.getSECURITY_HOTSPOT_DATA_KEY().is(dataId)) {
      return getSelectedSecurityHotspot();
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
    return new OpenFileDescriptor(project, securityHotspot.file(), offset);
  }

  @CheckForNull
  private LiveSecurityHotspot getSelectedSecurityHotspot() {
    var node = getSelectedNode();
    if (node instanceof LiveSecurityHotspotNode hotspotNode) {
      return hotspotNode.getHotspot();
    }
    return null;
  }

}
