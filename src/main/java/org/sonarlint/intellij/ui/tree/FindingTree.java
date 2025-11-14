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

import com.intellij.ide.DefaultTreeExpander;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.treeStructure.Tree;
import javax.annotation.CheckForNull;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.ui.nodes.FileNode;

public abstract class FindingTree extends Tree {

  private final Project project;

  FindingTree(Project project, TreeModel model) {
    super(model);
    this.project = project;
  }

  protected DataProvider getBackgroundDataProvider() {
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
  protected VirtualFile getSelectedFile() {
    var node = getSelectedNode();
    if (node instanceof FileNode fileNode) {
      return fileNode.file();
    }
    return null;
  }

  @CheckForNull
  protected DefaultMutableTreeNode getSelectedNode() {
    var path = getSelectionPath();
    if (path == null) {
      return null;
    }
    return (DefaultMutableTreeNode) path.getLastPathComponent();
  }

  protected Object getDataInner(@NonNls String dataId) {
    // use string literal as the key appeared in newer versions
    if ("bgtDataProvider".equals(dataId)) {
      return getBackgroundDataProvider();
    } else if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      return new DefaultTreeExpander(this);
    } else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return getSelectedFile();
    } else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      var f = getSelectedFile();
      // return empty so that it doesn't find it in parent components
      return f != null && f.isValid() ? (new VirtualFile[]{f}) : new VirtualFile[0];
    }

    return null;
  }

}
