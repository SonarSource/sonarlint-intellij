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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import javax.swing.Icon;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

public class FileNode extends AbstractNode {
  private static final Logger LOGGER = Logger.getInstance(FileNode.class);

  private final VirtualFile file;

  public FileNode(VirtualFile file) {
    this.file = file;
  }

  public VirtualFile file() {
    return file;
  }

  @Override
  public int getIssueCount() {
    return super.getChildCount();
  }

  @Override public int getFileCount() {
    return 1;
  }

  public Icon getIcon() {
    return file.getFileType().getIcon();
  }

  @Override public void render(TreeCellRenderer renderer) {
    renderer.setIcon(getIcon());
    renderer.append(file.getName());

    LOGGER.assertTrue(getIssueCount() > 0);
    String issues;

    if (getIssueCount() > 1) {
      issues = " issues)";
    } else {
      issues = " issue)";
    }
    renderer.append(spaceAndThinSpace() + "(" + getIssueCount() + issues, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
  }
}
